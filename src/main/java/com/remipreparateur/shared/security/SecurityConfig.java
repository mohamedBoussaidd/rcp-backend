package com.remipreparateur.shared.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ContexteFilter contexteFilter;
    private final PermissionAuthoritiesFilter permissionAuthoritiesFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ContexteFilter contexteFilter,
                          PermissionAuthoritiesFilter permissionAuthoritiesFilter,
                          CustomUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.contexteFilter = contexteFilter;
        this.permissionAuthoritiesFilter = permissionAuthoritiesFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // /error : sinon le forward interne ERROR (filtre JWT non rejoue)
                        // ecrase les 400/403 par un 401. Permettre /error preserve le vrai statut.
                        .requestMatchers("/api/auth/**", "/error").permitAll()

                        // ── Autorisation par PERMISSION (capability module:action), résolue par
                        // PermissionAuthoritiesFilter selon le contexte équipe actif. Les rôles ne
                        // sont plus cités ici : ajouter/retirer un accès = de la donnée (role_permission),
                        // plus du code. SUPER_ADMIN a toutes les permissions (bypass via le resolver).
                        // Le joueur lit SES séances via /api/moi/** (self-scope, hors de ce catalogue).

                        // Historique de présence (page dédiée) : isolé sous /api/presence pour être
                        // gardé par presence:write (et non seances:read qui couvre tout /api/seances).
                        .requestMatchers("/api/presence/**").hasAuthority("presence:write")

                        // Séances unifiées (cadre + exercices) : lecture / présence / écriture
                        .requestMatchers(HttpMethod.GET, "/api/seances/**").hasAuthority("seances:read")
                        .requestMatchers("/api/seances/*/presence/**").hasAuthority("presence:write")
                        .requestMatchers("/api/seances/**").hasAuthority("seances:write")

                        // Modèles de semaine (gabarits hebdo) : réutilise les droits séances
                        .requestMatchers(HttpMethod.GET, "/api/modeles-semaine/**").hasAuthority("seances:read")
                        .requestMatchers("/api/modeles-semaine/**").hasAuthority("seances:write")

                        // Saisons (cadre temporel) + périodes + effectif de saison
                        .requestMatchers(HttpMethod.GET, "/api/saisons/**").hasAuthority("saison:read")
                        .requestMatchers("/api/saisons/**").hasAuthority("saison:manage")

                        // Catalogue types de séance : cibles paramétrables (écriture) ; la LECTURE
                        // relève du Planning (socle) — nécessaire au calendrier et à la création de
                        // séance — donc gardée par seances:read (et NON predictions:read, sinon un club
                        // sans module Prépa physique ne pourrait plus créer de séance).
                        .requestMatchers(HttpMethod.PUT, "/api/type-seances/**").hasAuthority("typeseances:write")
                        .requestMatchers(HttpMethod.GET, "/api/type-seances/**").hasAuthority("seances:read")
                        // Prédictions / charge IA : module Prépa physique OU GPS (voir FeatureModule).
                        .requestMatchers(HttpMethod.GET, "/api/predictions/**").hasAuthority("predictions:read")

                        // Joueurs (effectif) : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/joueurs/**").hasAuthority("joueurs:read")
                        .requestMatchers("/api/joueurs/**").hasAuthority("joueurs:write")

                        // Pesées : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/pesees/**").hasAuthority("pesees:read")
                        .requestMatchers("/api/pesees/**").hasAuthority("pesees:write")

                        // Import Excel / données GPS
                        .requestMatchers("/api/import/**").hasAuthority("gps:import")

                        // Blessures (module médical) : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/blessures/**").hasAuthority("blessures:read")
                        .requestMatchers("/api/blessures/**").hasAuthority("blessures:write")

                        // Documents médicaux (staff) : lecture (filtrage fin par visibilité dans le
                        // service) ; dépôt/suppression. Le dépôt par le joueur passe par /api/moi/**.
                        .requestMatchers(HttpMethod.GET, "/api/documents-medicaux/**").hasAuthority("documents:read")
                        .requestMatchers("/api/documents-medicaux/**").hasAuthority("documents:write")

                        // Wellness (ressenti) + RPE : saisie joueur via /api/moi/** ; lecture staff ;
                        // réouverture d'une gêne (révision) vs traitement d'une gêne.
                        .requestMatchers(HttpMethod.GET, "/api/wellness/**", "/api/rpe/**").hasAuthority("wellness:read")
                        .requestMatchers("/api/wellness/*/gene-rouvrir").hasAuthority("wellness:reopen")
                        .requestMatchers("/api/wellness/**").hasAuthority("wellness:treat")

                        // Conseils du staff au joueur : lecture / écriture. Le joueur lit via /api/moi/conseils.
                        .requestMatchers(HttpMethod.GET, "/api/conseils/**").hasAuthority("conseils:read")
                        .requestMatchers("/api/conseils/**").hasAuthority("conseils:write")

                        // Suivi individuel — axes de travail & entretiens (staff). Le joueur passe par /api/moi/**.
                        // Suppression d'entretien élargie au modérateur (règle auteur/manage dans le service).
                        .requestMatchers(HttpMethod.GET, "/api/axes/**").hasAuthority("axe:read")
                        .requestMatchers("/api/axes/**").hasAuthority("axe:write")
                        .requestMatchers(HttpMethod.GET, "/api/entretiens/**").hasAuthority("entretien:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/entretiens/**").hasAnyAuthority("entretien:write", "entretien:manage")
                        .requestMatchers("/api/entretiens/**").hasAuthority("entretien:write")

                        // Espace personnel du joueur : reserve au role JOUEUR (donnees scopees par token)
                        .requestMatchers("/api/moi/**").hasRole("JOUEUR")

                        // Notifications — configuration (seuils, routage, droits, préférences ciblées).
                        .requestMatchers("/api/notifications/config/**",
                                "/api/notifications/routage/**",
                                "/api/notifications/droits/**",
                                "/api/notifications/preferences/joueur/**",
                                "/api/notifications/preferences/equipe/**").hasAuthority("notifications:config")
                        // Cloche, liste, marquage lu, /preferences/me, chat : tout utilisateur
                        // authentifié (staff ET joueur) — chacun ne voit que ses données.
                        .requestMatchers("/api/notifications/**").authenticated()

                        // Bibliothèque d'exercices (club) : lecture / écriture
                        // (édition/suppression restreinte au créateur/président dans le service)
                        .requestMatchers(HttpMethod.GET, "/api/exercices/**").hasAuthority("exercices:read")
                        .requestMatchers("/api/exercices/**").hasAuthority("exercices:write")

                        // Formations tactiques personnalisées (club) : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/formations/**").hasAuthority("formations:read")
                        .requestMatchers("/api/formations/**").hasAuthority("formations:write")

                        // Schémas tactiques (bibliothèque, club) : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/schemas/**").hasAuthority("schemas:read")
                        .requestMatchers("/api/schemas/**").hasAuthority("schemas:write")

                        // Plan de jeu (document d'identité équipe) : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/plan-de-jeu/**").hasAuthority("plandejeu:read")
                        .requestMatchers("/api/plan-de-jeu/**").hasAuthority("plandejeu:write")

                        // Match (cycle de vie avant/après, équipe) : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/matchs/**").hasAuthority("matchs:read")
                        .requestMatchers("/api/matchs/**").hasAuthority("matchs:write")

                        // Diaporamas : lecture / écriture ; suppression élargie au modérateur (règle créateur dans le service)
                        .requestMatchers(HttpMethod.GET, "/api/diaporamas/**").hasAuthority("diaporama:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/diaporamas/**").hasAnyAuthority("diaporama:write", "diaporama:manage")
                        .requestMatchers("/api/diaporamas/**").hasAuthority("diaporama:write")

                        // Configuration : lecture / écriture
                        .requestMatchers(HttpMethod.GET, "/api/configuration/**").hasAuthority("configuration:read")
                        .requestMatchers("/api/configuration/**").hasAuthority("configuration:write")

                        // Catégories d'âge : accessible depuis Paramètres (configuration:*) OU depuis
                        // Licences & documents (docadmin:*) — Administratif n'a pas configuration:write
                        // (il ne doit pas éditer les paramètres GPS) mais gère ses catégories d'âge
                        // depuis l'écran documents-admin, qu'il détient déjà via docadmin:configure.
                        .requestMatchers(HttpMethod.GET, "/api/categories-age/**").hasAnyAuthority("configuration:read", "docadmin:read")
                        .requestMatchers("/api/categories-age/**").hasAnyAuthority("configuration:write", "docadmin:configure")

                        // Licences & documents administratifs : référentiel / lecture / validation / dépôt.
                        // Le joueur dépose et consulte SES documents via /api/moi/**.
                        .requestMatchers(HttpMethod.GET, "/api/documents-admin/conformite", "/api/documents-admin/conformite/**",
                                "/api/documents-admin/conformite-staff").hasAuthority("docadmin:read")
                        .requestMatchers(HttpMethod.GET, "/api/documents-admin/*/fichier").hasAuthority("docadmin:read")
                        .requestMatchers("/api/documents-admin/types/**").hasAuthority("docadmin:configure")
                        .requestMatchers(HttpMethod.POST, "/api/documents-admin/*/valider", "/api/documents-admin/*/refuser")
                                .hasAuthority("docadmin:validate")
                        .requestMatchers(HttpMethod.POST, "/api/documents-admin/joueurs/**").hasAuthority("docadmin:upload")

                        // Clubs / mon-club / equipes / membres : deja proteges par @PreAuthorize.
                        // Tout le reste exige juste un token valide.
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(contexteFilter, JwtAuthenticationFilter.class)
                // Après le contexte (équipe active posée) : enrichit l'auth avec les permissions.
                .addFilterAfter(permissionAuthoritiesFilter, ContexteFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://sportgestions.fr",
                "http://192.168.1.62:4200",
                "https://www.sportgestions.fr"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
