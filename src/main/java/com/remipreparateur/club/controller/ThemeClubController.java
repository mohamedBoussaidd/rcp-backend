package com.remipreparateur.club.controller;

import com.remipreparateur.club.dto.GestionDtos.ThemeRequest;
import com.remipreparateur.club.dto.GestionDtos.ThemeResponse;
import com.remipreparateur.club.service.GestionClubService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Theme visuel du club (couleur d'accent + nav teintee).
 *   - Lecture : tout utilisateur authentifie (le joueur PWA doit recevoir le theme de son club).
 *   - Ecriture : president ({@code club:manage}) ou super-admin (via contexte club).
 */
@RestController
@RequestMapping("/api/club/theme")
public class ThemeClubController {

    private final GestionClubService gestion;
    private final CurrentUserProvider currentUser;

    public ThemeClubController(GestionClubService gestion, CurrentUserProvider currentUser) {
        this.gestion = gestion;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ThemeResponse theme() {
        return gestion.theme(currentUser.current());
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('club:manage')")
    public ThemeResponse definirTheme(@RequestBody ThemeRequest req) {
        return gestion.definirTheme(currentUser.current(), req);
    }
}
