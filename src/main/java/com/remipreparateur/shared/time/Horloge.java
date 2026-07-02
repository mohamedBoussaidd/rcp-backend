package com.remipreparateur.shared.time;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.shared.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Horloge de l'application : source UNIQUE du « jour courant » pour la LOGIQUE MÉTIER EN LECTURE.
 *
 * <p><b>Démonstration / voyage dans la saison</b> : un SUPER_ADMIN peut se placer à une date arbitraire
 * en envoyant l'en-tête {@code X-Date-Simulee} (capté par {@link HorlogeFilter}). L'horloge n'honore
 * cette date QUE pour un SUPER_ADMIN ; tout autre utilisateur — ou un appel sans en-tête — obtient
 * l'heure réelle. La date vit dans un ThreadLocal par requête → <b>aucun effet sur les autres sessions
 * ni sur les autres clubs</b>.
 *
 * <p><b>À NE PAS utiliser</b> pour : l'audit ({@code cree_le}/{@code maj_le}/{@code @PrePersist}), les
 * garde-fous d'écriture (ex. « on ne marque réalisée qu'une séance {@code date ≤ aujourd'hui} ») et les
 * schedulers — tout cela doit rester à l'heure réelle ({@link LocalDate#now()} / {@link LocalDateTime#now()}).
 */
@Component
public class Horloge {

    /** Jour courant métier : date simulée si l'appelant est SUPER_ADMIN, sinon date réelle. */
    public LocalDate today() {
        LocalDate simulee = HorlogeHolder.get();
        return (simulee != null && estSuperAdmin()) ? simulee : LocalDate.now();
    }

    /** Horodatage métier : date simulée (à l'heure courante) si SUPER_ADMIN, sinon instant réel. */
    public LocalDateTime now() {
        LocalDate simulee = HorlogeHolder.get();
        return (simulee != null && estSuperAdmin()) ? simulee.atTime(LocalTime.now()) : LocalDateTime.now();
    }

    /** true si une date simulée est active ET honorée (SUPER_ADMIN) pour la requête courante. */
    public boolean estSimulee() {
        return HorlogeHolder.get() != null && estSuperAdmin();
    }

    private boolean estSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof CustomUserDetails details
                && details.getUtilisateur().getRole() == Role.SUPER_ADMIN;
    }
}
