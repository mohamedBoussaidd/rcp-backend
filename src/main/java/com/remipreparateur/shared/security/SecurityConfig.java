package com.remipreparateur.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Roles autorises a LIRE les modules existants (lecture seule par defaut). */
    private static final String[] STAFF =
            { "ENTRAINEUR", "PREPARATEUR", "MEDICAL", "PRESIDENT", "SUPER_ADMIN" };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ContexteFilter contexteFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ContexteFilter contexteFilter,
                          CustomUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.contexteFilter = contexteFilter;
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

                        // ── Phase 3 : cloisonnement lecture (staff) vs ecriture (role proprietaire) ──
                        // Seances physiques : lecture = staff ; ecriture = preparateur seul
                        // (l'entraineur aura son propre module technique). Le joueur lit ses seances
                        // d'equipe en lecture seule via /api/moi/seances (scoping par token).
                        .requestMatchers(HttpMethod.GET, "/api/seances/**").hasAnyRole(STAFF)
                        // présence : l'entraîneur et le préparateur peuvent écrire
                        .requestMatchers("/api/seances/*/presence/**").hasAnyRole("ENTRAINEUR", "PREPARATEUR", "PRESIDENT", "SUPER_ADMIN")
                        // séance unifiée (cadre + exercices) : staff qui planifie l'entraînement
                        .requestMatchers("/api/seances/**").hasAnyRole("ENTRAINEUR", "PREPARATEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Catalogue types de seance : lecture staff ; cibles paramétrables = staff qui planifie
                        .requestMatchers(HttpMethod.PUT, "/api/type-seances/**").hasAnyRole("ENTRAINEUR", "PREPARATEUR", "PRESIDENT", "SUPER_ADMIN")
                        // Catalogue types de seance + predictions IA : lecture seule (staff)
                        .requestMatchers(HttpMethod.GET, "/api/type-seances/**", "/api/predictions/**").hasAnyRole(STAFF)

                        // Joueurs : lecture = staff ; ecriture = entraineur / preparateur
                        .requestMatchers(HttpMethod.GET, "/api/joueurs/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/joueurs/**").hasAnyRole("ENTRAINEUR", "PREPARATEUR", "SUPER_ADMIN")

                        // Pesees : lecture = staff ; ecriture = preparateur
                        .requestMatchers(HttpMethod.GET, "/api/pesees/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/pesees/**").hasAnyRole("PREPARATEUR", "SUPER_ADMIN")

                        // Import Excel / donnees GPS : preparateur uniquement
                        .requestMatchers("/api/import/**").hasAnyRole("PREPARATEUR", "SUPER_ADMIN")

                        // Blessures (module medical) : lecture = staff ; ecriture = medical
                        .requestMatchers(HttpMethod.GET, "/api/blessures/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/blessures/**").hasAnyRole("MEDICAL", "SUPER_ADMIN")

                        // Documents medicaux (staff) : lecture = staff (filtrage fin par visibilite
                        // dans le service) ; suppression = medical. Le depot/gestion par le joueur
                        // passe par /api/moi/documents-medicaux (regle /api/moi/** ci-dessous).
                        .requestMatchers(HttpMethod.GET, "/api/documents-medicaux/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/documents-medicaux/**").hasAnyRole("MEDICAL", "SUPER_ADMIN")

                        // Wellness (ressenti) + RPE de seance : saisie par le joueur via /api/moi/**,
                        // lecture staff via /api/wellness et /api/rpe (filtrage equipe en service).
                        .requestMatchers(HttpMethod.GET, "/api/wellness/**", "/api/rpe/**").hasAnyRole(STAFF)
                        // Réouverture d'une gêne (révision d'une décision) : médical seul
                        .requestMatchers("/api/wellness/*/gene-rouvrir").hasAnyRole("MEDICAL", "SUPER_ADMIN")
                        // Traitement d'une gêne : médical / préparateur
                        .requestMatchers("/api/wellness/**").hasAnyRole("MEDICAL", "PREPARATEUR", "SUPER_ADMIN")

                        // Conseils du staff au joueur : lecture = staff ; écriture = médical / préparateur.
                        // Le joueur lit ses conseils via /api/moi/conseils (regle /api/moi/** ci-dessous).
                        .requestMatchers(HttpMethod.GET, "/api/conseils/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/conseils/**").hasAnyRole("MEDICAL", "PREPARATEUR", "SUPER_ADMIN")

                        // Espace personnel du joueur : reserve au role JOUEUR (donnees scopees par token)
                        .requestMatchers("/api/moi/**").hasRole("JOUEUR")

                        // Bibliotheque d'exercices (club) : lecture staff ; ecriture entraineur/president
                        // (edition/suppression restreinte au createur/president dans le service)
                        .requestMatchers(HttpMethod.GET, "/api/exercices/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/exercices/**").hasAnyRole("ENTRAINEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Formations tactiques personnalisees (club) : lecture staff ; ecriture entraineur/president
                        .requestMatchers(HttpMethod.GET, "/api/formations/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/formations/**").hasAnyRole("ENTRAINEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Schemas tactiques (bibliotheque, club) : lecture staff ; ecriture entraineur/president
                        .requestMatchers(HttpMethod.GET, "/api/schemas/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/schemas/**").hasAnyRole("ENTRAINEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Plan de jeu (document d'identite equipe) : lecture staff ; ecriture entraineur/president
                        .requestMatchers(HttpMethod.GET, "/api/plan-de-jeu/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/plan-de-jeu/**").hasAnyRole("ENTRAINEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Match (cycle de vie avant/apres, equipe) : lecture staff ; ecriture entraineur/president
                        .requestMatchers(HttpMethod.GET, "/api/matchs/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/matchs/**").hasAnyRole("ENTRAINEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Configuration : lecture = staff ; ecriture = preparateur + president
                        .requestMatchers(HttpMethod.GET, "/api/configuration/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/configuration/**").hasAnyRole("PREPARATEUR", "PRESIDENT", "SUPER_ADMIN")

                        // Clubs / mon-club / equipes / membres : deja proteges par @PreAuthorize.
                        // Tout le reste exige juste un token valide.
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(contexteFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://sportgestions.fr",
                "https://www.sportgestions.fr"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
