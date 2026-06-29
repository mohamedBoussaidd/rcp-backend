package com.remipreparateur.auth.controller;

import com.remipreparateur.auth.dto.AuthResponse;
import com.remipreparateur.auth.dto.LoginRequest;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.shared.security.CustomUserDetails;
import com.remipreparateur.shared.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UtilisateurRepository utilisateurRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.utilisateurRepository = utilisateurRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getMotDePasse()));

            Utilisateur utilisateur = ((CustomUserDetails) authentication.getPrincipal()).getUtilisateur();
            String token = jwtService.generateToken(utilisateur);
            return ResponseEntity.ok(AuthResponse.of(token, utilisateur));
        } catch (DisabledException e) {
            // Compte désactivé (ex. joueur écarté de l'effectif de la saison → accès PWA coupé).
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Compte désactivé — contactez votre club");
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email ou mot de passe incorrect");
        }
    }

    /** Retourne l'utilisateur courant a partir du token (pratique pour le front au rechargement). */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Utilisateur u = principal.getUtilisateur();
        // Pas de nouveau token ici : on renvoie juste les infos (token = null cote reponse)
        return ResponseEntity.ok(AuthResponse.of(null, u));
    }
}
