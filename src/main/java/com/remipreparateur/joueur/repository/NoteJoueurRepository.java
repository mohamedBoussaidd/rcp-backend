package com.remipreparateur.joueur.repository;

import com.remipreparateur.joueur.entity.NoteJoueur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteJoueurRepository extends JpaRepository<NoteJoueur, UUID> {

    List<NoteJoueur> findByJoueurIdOrderByDateNoteDescCreatedAtDesc(UUID joueurId);
}
