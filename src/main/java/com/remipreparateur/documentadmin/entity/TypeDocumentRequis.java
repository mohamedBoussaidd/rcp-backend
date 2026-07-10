package com.remipreparateur.documentadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Référentiel configurable par club des documents exigés des joueurs (licence, certificat
 * médical, autorisation parentale...). {@code categoriesAge} = CSV de {@link CategorieAge#getCode()}
 * applicables ; {@code null} = s'applique à toutes les catégories.
 */
@Entity
@Table(name = "type_document_requis")
@Getter
@Setter
public class TypeDocumentRequis {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "description")
    private String description;

    @Column(name = "obligatoire", nullable = false)
    private boolean obligatoire = true;

    /** Si false, un dépôt joueur passe directement en VALIDE (pas de validation staff). */
    @Column(name = "validation_manuelle", nullable = false)
    private boolean validationManuelle = true;

    /** null = pas d'expiration. */
    @Column(name = "duree_validite_mois")
    private Short dureeValiditeMois;

    /** CSV de {@code categorie_age.code} ; null = toutes catégories. */
    @Column(name = "categories_age")
    private String categoriesAge;

    /** Public concerné : 'JOUEUR' (filtré par catégorie d'âge) | 'STAFF' | 'TOUS'. */
    @Column(name = "cible", nullable = false)
    private String cible = "JOUEUR";

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "ordre", nullable = false)
    private short ordre;
}
