package com.remipreparateur.ia.service;

import com.remipreparateur.ia.entity.IaFournisseur;
import com.remipreparateur.ia.repository.IaFournisseurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogue des fournisseurs IA : lecture (pour la résolution) et administration (super-admin).
 *
 * <p><strong>Pourquoi un catalogue en base.</strong> La clé globale ne pouvait venir que d'une
 * variable d'environnement : invisible depuis l'application, non modifiable sans toucher au serveur,
 * et silencieusement absente quand le processus n'héritait pas de la variable. Ici la clé est
 * saisissable, chiffrée, et son ORIGINE est affichable — voir {@link Origine}.
 *
 * <p><strong>Rétrocompatibilité.</strong> Tant qu'aucune clé n'est saisie, {@code ANTHROPIC} et
 * {@code OPENAI} retombent sur les variables d'environnement historiques : une installation
 * existante continue de marcher à l'identique.
 */
@Service
public class IaFournisseurService {

    /** Fournisseurs du socle : jamais supprimables (leur clé, elle, est révocable). */
    private static final List<String> SOCLE = List.of("ANTHROPIC", "OPENAI");

    /** Dialectes implémentés — un dialecte = un client Java ({@link LlmTextClient}). */
    public static final List<String> DIALECTES = List.of("OPENAI", "ANTHROPIC");

    /** D'où vient la clé effective d'un fournisseur : le témoin affiché à l'admin. */
    public enum Origine { BASE, ENVIRONNEMENT, AUCUNE }

    private final IaFournisseurRepository repository;
    private final CryptoService crypto;

    @Value("${ANTHROPIC_API_KEY:}")
    private String cleEnvAnthropic;
    @Value("${OPENAI_API_KEY:}")
    private String cleEnvOpenAi;

    public IaFournisseurService(IaFournisseurRepository repository, CryptoService crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    public Optional<IaFournisseur> parCode(String code) {
        return code == null || code.isBlank()
                ? Optional.empty()
                : repository.findById(code.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<IaFournisseur> tous() {
        return repository.findAllByOrderByLibelleAsc();
    }

    /**
     * Clé à utiliser pour ce fournisseur : celle saisie en base d'abord, sinon la variable
     * d'environnement historique (socle uniquement). {@code null} si aucune des deux.
     */
    public String cleEffective(String code) {
        String base = parCode(code).map(IaFournisseur::getCleChiffree)
                .filter(c -> !c.isBlank()).map(crypto::dechiffrer).orElse(null);
        if (base != null && !base.isBlank()) return base;
        String env = cleEnvironnement(code);
        return (env == null || env.isBlank()) ? null : env;
    }

    /** Variable d'environnement du serveur pour ce fournisseur (socle uniquement), sinon null. */
    public String cleEnvironnement(String code) {
        if (code == null) return null;
        Map<String, String> env = Map.of("ANTHROPIC", cleEnvAnthropic == null ? "" : cleEnvAnthropic,
                                         "OPENAI", cleEnvOpenAi == null ? "" : cleEnvOpenAi);
        String v = env.get(code.trim().toUpperCase(Locale.ROOT));
        return (v == null || v.isBlank()) ? null : v;
    }

    /** Origine de la clé effective — alimente le témoin « clé détectée (base) / (environnement) / aucune ». */
    public Origine origine(String code) {
        boolean enBase = parCode(code).map(IaFournisseur::getCleChiffree)
                .filter(c -> !c.isBlank()).isPresent();
        if (enBase) return Origine.BASE;
        return cleEnvironnement(code) != null ? Origine.ENVIRONNEMENT : Origine.AUCUNE;
    }

    /** Clé effective masquée pour l'affichage (« sk-ant-…a1b2 »), ou null s'il n'y en a pas. */
    public String cleMasquee(String code) {
        return CryptoService.masquer(cleEffective(code));
    }

    // ── Vue admin ──

    /**
     * Ligne d'écran d'un fournisseur. {@code origineCle} est le témoin qui manquait : il dit d'où
     * vient (ou ne vient pas) la clé, au lieu de laisser deviner devant une IA muette.
     */
    public record FournisseurDto(String code, String libelle, String dialecte, String baseUrl,
                                 String modeleDefaut, boolean actif, boolean socle,
                                 String origineCle, String cleMasquee) {}

    @Transactional(readOnly = true)
    public List<FournisseurDto> catalogue() {
        return tous().stream().map(this::dto).toList();
    }

    public FournisseurDto dto(IaFournisseur f) {
        return new FournisseurDto(f.getCode(), f.getLibelle(), f.getDialecte(), f.getBaseUrl(),
                f.getModeleDefaut(), f.isActif(), estDuSocle(f.getCode()),
                origine(f.getCode()).name(), cleMasquee(f.getCode()));
    }

    // ── Administration ──

    /**
     * Crée ou met à jour un fournisseur. Une clé vide CONSERVE la clé existante (on ne peut pas
     * réafficher une clé pour la re-soumettre : la vider par mégarde serait trop facile) ;
     * {@link #revoquerCle} est la façon explicite de l'effacer.
     */
    @Transactional
    public IaFournisseur enregistrer(String code, String libelle, String dialecte, String baseUrl,
                                     String modeleDefaut, Boolean actif, String cleApi) {
        String cleCode = normaliserCode(code);
        IaFournisseur f = repository.findById(cleCode).orElseGet(() -> {
            IaFournisseur n = new IaFournisseur();
            n.setCode(cleCode);
            return n;
        });
        if (libelle != null && !libelle.isBlank()) f.setLibelle(libelle.trim());
        if (f.getLibelle() == null || f.getLibelle().isBlank()) f.setLibelle(cleCode);

        if (dialecte != null && !dialecte.isBlank()) {
            String d = dialecte.trim().toUpperCase(Locale.ROOT);
            if (!DIALECTES.contains(d)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Dialecte inconnu : " + d + " (attendu : " + String.join(" ou ", DIALECTES) + ")");
            }
            f.setDialecte(d);
        }
        if (f.getDialecte() == null) f.setDialecte("OPENAI");

        if (baseUrl != null) f.setBaseUrl(baseUrl.isBlank() ? null : sansSlashFinal(baseUrl.trim()));
        if (modeleDefaut != null) f.setModeleDefaut(modeleDefaut.isBlank() ? null : modeleDefaut.trim());
        if (actif != null) f.setActif(actif);
        if (cleApi != null && !cleApi.isBlank()) f.setCleChiffree(crypto.chiffrer(cleApi.trim()));

        // Un dialecte OpenAI sans URL ne sait pas où appeler : seul OpenAI lui-même a un défaut.
        if ("OPENAI".equals(f.getDialecte()) && f.getBaseUrl() == null && !"OPENAI".equals(cleCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL de base requise pour un fournisseur au dialecte OpenAI (ex. https://api.mistral.ai/v1)");
        }
        f.setMajLe(LocalDateTime.now());
        return repository.save(f);
    }

    /** Efface la clé saisie — le fournisseur retombe sur sa variable d'environnement, s'il en a une. */
    @Transactional
    public void revoquerCle(String code) {
        IaFournisseur f = exiger(code);
        f.setCleChiffree(null);
        f.setMajLe(LocalDateTime.now());
        repository.save(f);
    }

    /** Supprime un fournisseur ajouté par l'admin. Les deux du socle ne sont pas supprimables. */
    @Transactional
    public void supprimer(String code) {
        IaFournisseur f = exiger(code);
        if (SOCLE.contains(f.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Fournisseur du socle : sa clé est révocable, mais il ne peut pas être supprimé.");
        }
        repository.delete(f);
    }

    public boolean estDuSocle(String code) {
        return code != null && SOCLE.contains(code.trim().toUpperCase(Locale.ROOT));
    }

    private IaFournisseur exiger(String code) {
        return parCode(code).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Fournisseur IA inconnu : " + code));
    }

    private static String normaliserCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code fournisseur obligatoire");
        }
        String c = code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!c.matches("[A-Z0-9_]{2,40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Code fournisseur invalide (2 à 40 caractères : lettres, chiffres, _)");
        }
        return c;
    }

    private static String sansSlashFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
