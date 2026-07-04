package com.remipreparateur.entretien.repository;

import com.remipreparateur.entretien.entity.Entretien;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EntretienRepository extends JpaRepository<Entretien, UUID> {

    List<Entretien> findByJoueurIdOrderByDateEntretienDescCreatedAtDesc(UUID joueurId);

    List<Entretien> findByJoueurIdAndVisibiliteOrderByDateEntretienDescCreatedAtDesc(UUID joueurId, String visibilite);

    List<Entretien> findByJoueurIdInOrderByDateEntretienDescCreatedAtDesc(List<UUID> joueurIds);

    // ── Agenda (calendrier) : entretiens d'une période, portée équipe ou globale ──

    List<Entretien> findByDateEntretienBetweenOrderByDateEntretienAscHeureAsc(LocalDate debut, LocalDate fin);

    List<Entretien> findByDateEntretienBetweenAndEquipeIdInOrderByDateEntretienAscHeureAsc(
            LocalDate debut, LocalDate fin, List<UUID> equipeIds);

    /** Agenda PWA joueur : ses rendez-vous PLANIFIE de la période (self-scope). */
    List<Entretien> findByJoueurIdAndStatutAndDateEntretienBetweenOrderByDateEntretienAscHeureAsc(
            UUID joueurId, String statut, LocalDate debut, LocalDate fin);
}
