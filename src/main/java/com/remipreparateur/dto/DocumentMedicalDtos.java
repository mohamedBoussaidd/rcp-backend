package com.remipreparateur.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du module documents medicaux. */
public final class DocumentMedicalDtos {

    private DocumentMedicalDtos() {}

    /** Mise a jour du partage par role (liste des roles autorises EN PLUS du medical). */
    public record PartageRequest(List<String> partageRoles) {}

    public record DocumentMedicalResponse(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            String nomOriginal,
            String typeMime,
            long tailleOctets,
            String categorie,
            String description,
            List<String> partageRoles,
            LocalDateTime dateDepot) {}
}
