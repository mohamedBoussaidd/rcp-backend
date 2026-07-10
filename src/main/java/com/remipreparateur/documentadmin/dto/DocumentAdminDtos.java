package com.remipreparateur.documentadmin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du domaine documentadmin (catégories d'âge, référentiel, conformité, documents). */
public final class DocumentAdminDtos {

    private DocumentAdminDtos() {}

    // ── Catégories d'âge ──
    public record CategorieAgeRequest(
            String code, String libelle, Integer ageMin, Integer ageMax, Integer ordre, Boolean actif) {}

    public record CategorieAgeResponse(
            UUID id, String code, String libelle, Integer ageMin, Integer ageMax, int ordre, boolean actif) {}

    // ── Référentiel des types de documents ──
    public record TypeDocumentRequisRequest(
            String code, String libelle, String description, boolean obligatoire, boolean validationManuelle,
            Integer dureeValiditeMois, List<String> categoriesAge, String cible, Integer ordre, Boolean actif) {}

    public record TypeDocumentRequisResponse(
            UUID id, String code, String libelle, String description, boolean obligatoire, boolean validationManuelle,
            Integer dureeValiditeMois, List<String> categoriesAge, String cible, int ordre, boolean actif) {}

    // ── Matrice de conformité ──
    public record StatutDocument(
            UUID typeId, String typeCode, String typeLibelle, boolean obligatoire,
            UUID documentId, String statut, LocalDate dateExpiration, String motifRefus) {}

    public record JoueurConformite(
            UUID joueurId, String nom, String prenom, String categorieAgeCode, List<StatutDocument> documents) {}

    public record ConformiteResponse(
            List<JoueurConformite> joueurs, int complets, int incomplets, int aValider, int expirentSous30j) {}

    // ── Documents (staff) ──
    public record RefusRequest(String motif) {}

    public record DocumentResponse(
            UUID id, UUID joueurId, UUID typeDocumentRequisId, String statut, String nomOriginal, String typeMime,
            Long tailleOctets, LocalDateTime dateSoumission, LocalDateTime dateValidation, String motifRefus,
            LocalDate dateExpiration) {}

    // ── Espace joueur ──
    public record MonDocumentResponse(
            UUID typeId, String typeLibelle, String typeDescription, boolean obligatoire, UUID documentId,
            String statut, String nomOriginal, LocalDate dateExpiration, String motifRefus) {}
}
