package com.remipreparateur.shared.time;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Capte l'en-tête {@code X-Date-Simulee} (yyyy-MM-dd) et le place dans {@link HorlogeHolder} pour la
 * durée de la requête. Ce filtre ne DÉCIDE PAS d'honorer la date : c'est {@link Horloge} qui, au
 * moment de l'appel (SecurityContext déjà peuplé), ne l'applique qu'à un SUPER_ADMIN. Une valeur
 * absente ou invalide laisse l'horloge à l'heure réelle.
 */
@Component
public class HorlogeFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Date-Simulee";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            LocalDate date = parse(request.getHeader(HEADER));
            if (date != null) HorlogeHolder.set(date);
            filterChain.doFilter(request, response);
        } finally {
            HorlogeHolder.clear();
        }
    }

    /** Parse tolérant : accepte yyyy-MM-dd (ou un ISO plus long dont on ne garde que la date). */
    private LocalDate parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return LocalDate.parse(v.substring(0, Math.min(10, v.length())));
        } catch (Exception e) {
            return null;
        }
    }
}
