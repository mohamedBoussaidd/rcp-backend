package com.remipreparateur.club.pack.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Pack commercial = bundle de modules {@code activables}. Les packs prédéfinis (seed) ne sont pas
 * supprimables ; le super-admin peut en créer/éditer d'autres (réutilisables) et définir leur prix.
 * Une modification de pack se propage aux clubs qui l'utilisent (résolution live), sauf surcharge
 * explicite du club (table {@code club_module}).
 */
@Entity
@Table(name = "pack")
@Getter
@Setter
public class Pack {

    @Id
    @Column(name = "code")
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(columnDefinition = "text")
    private String description;

    /** Prix mensuel (éditable par le super-admin), {@code null} tant que non défini. */
    @Column(name = "prix_mensuel")
    private BigDecimal prixMensuel;

    @Column(nullable = false)
    private int ordre = 0;

    @Column(nullable = false)
    private boolean actif = true;

    /** Pack seed (Essentiel/Prépa/Performance/Complet) : non supprimable. */
    @Column(nullable = false)
    private boolean predefini = false;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    /** Codes des modules inclus (validés contre {@code FeatureModule} dans le service). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pack_module", joinColumns = @JoinColumn(name = "pack_code"))
    @Column(name = "module_code")
    private Set<String> modules = new HashSet<>();
}
