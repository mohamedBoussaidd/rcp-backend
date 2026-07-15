package com.remipreparateur.contrat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage physique des fichiers du module Contrats & paie (contrats signés, bulletins).
 * Hors web-root (app.contrats.upload-dir, défaut ./data/contrats), nom généré anti-traversal —
 * même recette que les documents médicaux/administratifs.
 */
@Service
public class FichierContratStockage {

    private static final long TAILLE_MAX = 10L * 1024 * 1024; // 10 Mo
    private static final Map<String, String> MIME_EXT = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png");

    private final Path uploadDir;

    public FichierContratStockage(@Value("${app.contrats.upload-dir:./data/contrats}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de creer le dossier de stockage contrats : " + this.uploadDir, e);
        }
    }

    public record Stockage(String nomPhysique, String mime, String ext, String nomOriginal, long taille) {}
    public record Fichier(Resource resource, String nomOriginal, String typeMime) {}

    public Stockage stocker(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier manquant");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (max 10 Mo)");
        }
        String mime = fichier.getContentType();
        String ext = mime == null ? null : MIME_EXT.get(mime);
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de fichier non autorise (PDF, JPG, PNG)");
        }
        String nomPhysique = UUID.randomUUID() + "." + ext;
        Path cible = uploadDir.resolve(nomPhysique).normalize();
        if (!cible.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chemin de stockage invalide");
        }
        try {
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement du fichier", e);
        }
        return new Stockage(nomPhysique, mime, ext, nettoyerNom(fichier.getOriginalFilename(), ext), fichier.getSize());
    }

    public Fichier charger(String cheminStockage, String nomOriginal, String typeMime) {
        Path p = uploadDir.resolve(cheminStockage).normalize();
        if (!p.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        try {
            Resource r = new UrlResource(p.toUri());
            if (!r.exists() || !r.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable sur le serveur");
            }
            return new Fichier(r, nomOriginal, typeMime);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Chemin de fichier invalide", e);
        }
    }

    public void supprimer(String cheminStockage) {
        if (cheminStockage == null) return;
        Path p = uploadDir.resolve(cheminStockage).normalize();
        if (p.startsWith(uploadDir)) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // Fichier déjà disparu : on continue, la ligne base est la source de vérité.
            }
        }
    }

    private String nettoyerNom(String nom, String ext) {
        if (nom == null || nom.isBlank()) return "document." + ext;
        String base = nom.replaceAll(".*[/\\\\]", "").trim();
        if (base.isEmpty()) base = "document." + ext;
        return base.length() > 255 ? base.substring(0, 255) : base;
    }
}
