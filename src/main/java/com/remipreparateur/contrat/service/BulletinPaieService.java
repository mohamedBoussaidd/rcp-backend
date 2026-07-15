package com.remipreparateur.contrat.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.contrat.dto.ContratDtos.BulletinLigne;
import com.remipreparateur.contrat.dto.ContratDtos.DistributionResultat;
import com.remipreparateur.contrat.dto.ContratDtos.MonBulletin;
import com.remipreparateur.contrat.entity.BulletinPaie;
import com.remipreparateur.contrat.repository.BulletinPaieRepository;
import com.remipreparateur.contrat.service.FichierContratStockage.Fichier;
import com.remipreparateur.contrat.service.FichierContratStockage.Stockage;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fiches de paye : transmission pure (aucune logique comptable). Dépôt par le gestionnaire
 * (contrats:manage) → « Distribuer » notifie chaque personne (notifie_le) → la personne
 * télécharge (premier_telechargement_le timbré). Suivi de distribution = ces trois jalons.
 * La personne ne voit un bulletin qu'une fois distribué.
 */
@Service
public class BulletinPaieService {

    private final BulletinPaieRepository repository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AppartenanceService appartenance;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final FichierContratStockage stockage;
    private final NotificationProducer notifications;

    public BulletinPaieService(BulletinPaieRepository repository, JoueurRepository joueurRepository,
                               UtilisateurRepository utilisateurRepository, AppartenanceService appartenance,
                               ScopeResolver scopeResolver, CurrentUserProvider currentUser,
                               FichierContratStockage stockage, NotificationProducer notifications) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.appartenance = appartenance;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.stockage = stockage;
        this.notifications = notifications;
    }

    // ──────────────────────────── Gestion (contrats:manage) ────────────────────────────

    public List<LocalDate> periodes() {
        return repository.periodes(scopeResolver.clubActif());
    }

    public List<BulletinLigne> lignes(String periode) {
        UUID clubId = scopeResolver.clubActif();
        List<BulletinPaie> bulletins = repository.findByClubIdAndPeriodeOrderByDeposeLeDesc(clubId, periodeDe(periode));
        Map<UUID, Joueur> fiches = joueurRepository.findAllById(
                        bulletins.stream().map(BulletinPaie::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return bulletins.stream().map(b -> toLigne(b, fiches.get(b.getJoueurId()))).toList();
    }

    /** Dépôt (ou remplacement si la personne a déjà un bulletin sur la période). */
    public BulletinLigne deposer(UUID joueurId, String periode, MultipartFile fichier) {
        UUID clubId = scopeResolver.clubActif();
        Joueur fiche = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiche personne introuvable"));
        if (fiche.getClubId() != null && !fiche.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiche d'un autre club");
        }
        LocalDate p = periodeDe(periode);
        Stockage s = stockage.stocker(fichier);

        BulletinPaie b = repository.findByClubIdAndJoueurIdAndPeriode(clubId, joueurId, p)
                .map(existant -> {
                    stockage.supprimer(existant.getCheminStockage());
                    return existant;
                })
                .orElseGet(() -> {
                    BulletinPaie neuf = new BulletinPaie();
                    neuf.setClubId(clubId);
                    neuf.setJoueurId(joueurId);
                    neuf.setPeriode(p);
                    return neuf;
                });
        b.setNomOriginal(s.nomOriginal());
        b.setTypeMime(s.mime());
        b.setTailleOctets(s.taille());
        b.setCheminStockage(s.nomPhysique());
        b.setDeposePar(currentUser.current().getId());
        return toLigne(repository.save(b), fiche);
    }

    /**
     * Distribue la période : chaque bulletin non encore notifié est timbré (notifie_le) et la
     * personne reçoit une notification (in-app + push si abonnée). Le lien s'adapte au rôle du
     * compte lié (PWA joueur ou espace staff).
     */
    @Transactional
    public DistributionResultat distribuer(String periode) {
        UUID clubId = scopeResolver.clubActif();
        LocalDate p = periodeDe(periode);
        List<BulletinPaie> aDistribuer = repository.findByClubIdAndPeriodeAndNotifieLeIsNull(clubId, p);
        String label = labelPeriode(p);
        int notifies = 0;
        for (BulletinPaie b : aDistribuer) {
            b.setNotifieLe(LocalDateTime.now());
            repository.save(b);
            String lien = utilisateurRepository.findByJoueurId(b.getJoueurId())
                    .map(u -> u.getRole() == Role.JOUEUR ? "/joueur/bulletins" : "/staff/documents")
                    .orElse(null);
            if (notifications.bulletinDisponible(appartenance.equipePrincipale(b.getJoueurId()),
                    b.getJoueurId(), label, lien)) {
                notifies++;
            }
        }
        return new DistributionResultat(aDistribuer.size(), notifies);
    }

    public Fichier chargerFichier(UUID id) {
        BulletinPaie b = bulletinChecke(id);
        return stockage.charger(b.getCheminStockage(), b.getNomOriginal(), b.getTypeMime());
    }

    public void supprimer(UUID id) {
        BulletinPaie b = bulletinChecke(id);
        stockage.supprimer(b.getCheminStockage());
        repository.delete(b);
    }

    // ──────────────────────────── Espace personnel (self-scope) ────────────────────────────

    /** Mes bulletins : uniquement ceux déjà distribués. */
    public List<MonBulletin> mesBulletins(UUID joueurId) {
        return repository.findByJoueurIdAndNotifieLeIsNotNullOrderByPeriodeDesc(joueurId).stream()
                .map(b -> new MonBulletin(b.getId(), b.getPeriode(), b.getNomOriginal(),
                        b.getNotifieLe(), b.getPremierTelechargementLe()))
                .toList();
    }

    /** Téléchargement par la personne : timbre le premier téléchargement (suivi de distribution). */
    @Transactional
    public Fichier chargerMonBulletin(UUID joueurId, UUID id) {
        BulletinPaie b = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulletin introuvable"));
        if (!b.getJoueurId().equals(joueurId) || b.getNotifieLe() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulletin introuvable");
        }
        if (b.getPremierTelechargementLe() == null) {
            b.setPremierTelechargementLe(LocalDateTime.now());
            repository.save(b);
        }
        return stockage.charger(b.getCheminStockage(), b.getNomOriginal(), b.getTypeMime());
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private BulletinPaie bulletinChecke(UUID id) {
        BulletinPaie b = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulletin introuvable"));
        scopeResolver.verifieAccesClub(b.getClubId());
        return b;
    }

    /** "yyyy-MM" → 1er jour du mois. */
    private LocalDate periodeDe(String periode) {
        try {
            return LocalDate.parse(periode + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Période invalide (attendu yyyy-MM)");
        }
    }

    private String labelPeriode(LocalDate p) {
        return p.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + p.getYear();
    }

    private BulletinLigne toLigne(BulletinPaie b, Joueur fiche) {
        return new BulletinLigne(b.getId(), b.getJoueurId(),
                fiche != null ? fiche.getNom() : null,
                fiche != null ? fiche.getPrenom() : null,
                b.getPeriode(), b.getNomOriginal(), b.getDeposeLe(), b.getNotifieLe(),
                b.getPremierTelechargementLe());
    }
}
