package com.remipreparateur.dto;

import com.remipreparateur.entity.Utilisateur;

import java.util.UUID;

/** Reponse de login : le token + les infos utilisateur utiles au front. */
public class AuthResponse {

    private String token;
    private final String type = "Bearer";
    private UUID id;
    private String email;
    private String nom;
    private String prenom;
    private String role;
    private String specialite;
    private UUID clubId;
    private UUID equipeId;
    private UUID joueurId;

    public static AuthResponse of(String token, Utilisateur u) {
        AuthResponse r = new AuthResponse();
        r.token = token;
        r.id = u.getId();
        r.email = u.getEmail();
        r.nom = u.getNom();
        r.prenom = u.getPrenom();
        r.role = u.getRole().name();
        r.specialite = u.getSpecialite();
        r.clubId = u.getClubId();
        r.equipeId = u.getEquipeId();
        r.joueurId = u.getJoueurId();
        return r;
    }

    public String getToken() { return token; }
    public String getType() { return type; }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getRole() { return role; }
    public String getSpecialite() { return specialite; }
    public UUID getClubId() { return clubId; }
    public UUID getEquipeId() { return equipeId; }
    public UUID getJoueurId() { return joueurId; }
}
