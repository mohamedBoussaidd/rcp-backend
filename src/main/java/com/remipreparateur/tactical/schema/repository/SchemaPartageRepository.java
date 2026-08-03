package com.remipreparateur.tactical.schema.repository;

import com.remipreparateur.tactical.schema.entity.SchemaPartage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SchemaPartageRepository extends JpaRepository<SchemaPartage, UUID> {

    List<SchemaPartage> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    List<SchemaPartage> findBySchemaIdOrderByCreatedAtDesc(UUID schemaId);

    /**
     * Ce que voit UN joueur : les partages nominatifs qui le visent, plus ceux adressés à l'une
     * de ses équipes. La liste d'équipes est passée par l'appelant (portée saison) plutôt que
     * lue depuis la fiche : un joueur peut appartenir à plusieurs équipes.
     */
    @Query("""
           SELECT p FROM SchemaPartage p
           WHERE p.joueurId = :joueurId
              OR (p.equipeId IS NOT NULL AND p.equipeId IN :equipes)
           ORDER BY p.createdAt DESC
           """)
    List<SchemaPartage> pourJoueur(@Param("joueurId") UUID joueurId,
                                   @Param("equipes") Collection<UUID> equipes);
}
