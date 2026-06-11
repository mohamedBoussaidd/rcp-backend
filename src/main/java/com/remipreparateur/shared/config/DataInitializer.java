package com.remipreparateur.shared.config;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cree le compte SUPER_ADMIN au demarrage s'il n'existe pas encore. */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(UtilisateurRepository utilisateurRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.superadmin.email}") String adminEmail,
                           @Value("${app.superadmin.password}") String adminPassword) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (utilisateurRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }
        Utilisateur admin = new Utilisateur();
        admin.setEmail(adminEmail);
        admin.setMotDePasse(passwordEncoder.encode(adminPassword));
        admin.setNom("Super");
        admin.setPrenom("Admin");
        admin.setRole(Role.SUPER_ADMIN);
        admin.setActif(true);
        utilisateurRepository.save(admin);
        log.info("Super-admin cree : {} (pensez a changer le mot de passe)", adminEmail);
    }
}
