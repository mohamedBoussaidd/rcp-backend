package com.remipreparateur.ia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Fournisseur IA du catalogue (super-admin) : de quoi appeler une API LLM sans redéployer.
 *
 * <p>Le champ décisif est {@link #dialecte} — le PROTOCOLE parlé, pas la marque. Deux valeurs :
 * {@code OPENAI} (POST {@code {baseUrl}/chat/completions}, en-tête {@code Authorization: Bearer} —
 * dialecte de Mistral, Groq, DeepSeek, Together, OpenRouter, Ollama…) et {@code ANTHROPIC} (SDK
 * officiel). Ajouter un fournisseur qui parle l'un des deux = une ligne, aucun code.
 *
 * <p>{@link #cleChiffree} est chiffrée (AES-GCM, {@code CryptoService}) et n'est jamais renvoyée en
 * clair par l'API. Vide = repli sur la variable d'environnement du serveur, cf. {@code IaConfigResolver}.
 */
@Entity
@Table(name = "ia_fournisseur")
@Getter
@Setter
public class IaFournisseur {

    /** Identifiant stable, en majuscules (ex. {@code MISTRAL}) — référencé par la config des clubs. */
    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    /** OPENAI | ANTHROPIC — le protocole à parler (voir la javadoc de la classe). */
    @Column(name = "dialecte", nullable = false, length = 20)
    private String dialecte;

    /** Racine de l'API, sans slash final (ex. {@code https://api.mistral.ai/v1}). */
    @Column(name = "base_url", length = 200)
    private String baseUrl;

    @Column(name = "cle_chiffree", columnDefinition = "text")
    private String cleChiffree;

    @Column(name = "modele_defaut", length = 80)
    private String modeleDefaut;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "maj_le")
    private LocalDateTime majLe;
}
