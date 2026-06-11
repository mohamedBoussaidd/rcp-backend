package com.remipreparateur.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lit le contexte de navigation actif dans les en-têtes HTTP et le place dans
 * {@link ContexteActifHolder} pour la durée de la requête :
 * <ul>
 *   <li>{@code X-Contexte-Club}     : UUID du club actif</li>
 *   <li>{@code X-Contexte-Equipes}  : CSV d'UUID d'équipes (vide = toutes les équipes du club)</li>
 * </ul>
 * Les valeurs invalides sont ignorées silencieusement (contexte = vide → scope identité).
 */
@Component
public class ContexteFilter extends OncePerRequestFilter {

    private static final String HEADER_CLUB = "X-Contexte-Club";
    private static final String HEADER_EQUIPES = "X-Contexte-Equipes";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            UUID clubId = parseUuid(request.getHeader(HEADER_CLUB));
            List<UUID> equipeIds = parseUuidCsv(request.getHeader(HEADER_EQUIPES));
            if (clubId != null || !equipeIds.isEmpty()) {
                ContexteActifHolder.set(new ContexteActif(clubId, equipeIds));
            }
            filterChain.doFilter(request, response);
        } finally {
            ContexteActifHolder.clear();
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<UUID> parseUuidCsv(String value) {
        List<UUID> ids = new ArrayList<>();
        if (value == null || value.isBlank()) return ids;
        for (String part : value.split(",")) {
            UUID id = parseUuid(part);
            if (id != null) ids.add(id);
        }
        return ids;
    }
}
