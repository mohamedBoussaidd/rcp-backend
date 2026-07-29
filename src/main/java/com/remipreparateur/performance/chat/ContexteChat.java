package com.remipreparateur.performance.chat;

import com.remipreparateur.auth.rbac.Permission;

/**
 * Fournisseur d'un bloc de CONTEXTE métier pour le chat. C'est le point d'extension du chat aux
 * autres métiers : ouvrir l'assistant à l'entraîneur, au médical ou au président = ajouter une
 * implémentation, sans rien changer au {@link ChatService} ni au front.
 *
 * <p><strong>Garantie de confidentialité.</strong> {@link ChatService} n'assemble que les blocs dont
 * l'utilisateur détient {@link #permissionRequise()}. Les données qu'il n'a pas le droit de voir ne
 * sont donc JAMAIS écrites dans le prompt : elles sont filtrées à la source, pas par une consigne
 * adressée au modèle (qu'une injection pourrait contourner). Un médecin sans {@code predictions:read}
 * ne peut pas faire ressortir la charge de l'effectif, même en le demandant explicitement.
 *
 * <p>Corollaire : une implémentation ne doit produire que des INDICATEURS DÉJÀ CALCULÉS et agrégés,
 * jamais de données brutes ni d'identifiants techniques.
 */
public interface ContexteChat {

    /** Permission requise pour que ce bloc soit injecté dans le prompt. */
    Permission permissionRequise();

    /** Libellé court du domaine couvert (ex. « Préparation physique ») — sert aussi d'en-tête au bloc. */
    String libelle();

    /**
     * Bloc de texte factuel injecté dans le prompt, ou {@code null} / vide si rien d'exploitable.
     * Appelé à chaque message : garder l'appel court et tolérant aux erreurs.
     */
    String bloc();
}
