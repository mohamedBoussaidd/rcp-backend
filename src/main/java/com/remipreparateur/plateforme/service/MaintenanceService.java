package com.remipreparateur.plateforme.service;

import com.remipreparateur.contrat.service.ContratEcheanceScheduler;
import com.remipreparateur.notification.service.NotificationPurgeService;
import com.remipreparateur.notification.service.NotificationScheduler;
import com.remipreparateur.plateforme.entity.TacheExecution;
import com.remipreparateur.plateforme.repository.TacheExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

/**
 * Registre des tâches de maintenance déclenchables à la main par le super-admin (et, pour
 * certaines, planifiées par ailleurs). Exécute la tâche, mesure, journalise le résultat dans
 * {@code tache_execution}, et expose le dernier statut de chacune pour la console.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    /** Une tâche : action (renvoie un message de résultat) + aperçu facultatif (volume en attente). */
    private record Tache(String code, String libelle, String description,
                         boolean nettoyage, LongSupplier apercu, Callable<String> action) {}

    private final TacheExecutionRepository executionRepository;
    private final Map<String, Tache> taches = new LinkedHashMap<>();

    public MaintenanceService(TacheExecutionRepository executionRepository,
                              NotificationPurgeService purgeService,
                              NettoyageService nettoyageService,
                              NotificationScheduler notificationScheduler,
                              ContratEcheanceScheduler contratScheduler) {
        this.executionRepository = executionRepository;

        enregistrer(new Tache("purge_notifications", "Purge des notifications",
                "Supprime les notifications au-delà des délais de rétention (lues / non lues).",
                true, null,
                () -> purgeService.purger() + " notification(s) supprimée(s)"));

        enregistrer(new Tache("push_orphelins", "Abonnements push fantômes",
                "Supprime les abonnements Web Push de comptes supprimés ou désactivés.",
                true, nettoyageService::compterPushOrphelins,
                () -> nettoyageService.purgerPushOrphelins() + " abonnement(s) supprimé(s)"));

        enregistrer(new Tache("notifs_orphelines", "Notifications orphelines",
                "Supprime les notifications dont le compte destinataire n'existe plus.",
                true, nettoyageService::compterNotifsOrphelines,
                () -> nettoyageService.purgerNotifsOrphelines() + " notification(s) supprimée(s)"));

        enregistrer(new Tache("docs_admin_expiration", "Expiration documents administratifs",
                "Passe en EXPIRÉ les documents dont la validité est dépassée et notifie les joueurs.",
                false, null,
                () -> { notificationScheduler.expirerDocumentsAdmin(); return "Exécuté"; }));

        enregistrer(new Tache("docs_admin_relance", "Relance + digest documents",
                "Relance les joueurs incomplets et envoie le digest de conformité aux Président/Administratif.",
                false, null,
                () -> { notificationScheduler.relanceEtDigestDocumentsAdmin(); return "Exécuté"; }));

        enregistrer(new Tache("retours_blessure", "Réconciliation des retours de blessure",
                "Solde les blessures dont la date de retour est atteinte et notifie le staff pour confirmation.",
                false, null,
                () -> { notificationScheduler.reconcilierRetoursBlessure(); return "Exécuté"; }));

        enregistrer(new Tache("contrats_echeances", "Échéances de contrat (J-90 / J-30)",
                "Notifie les échéances de contrat aux jalons J-90 et J-30.",
                false, null,
                () -> { contratScheduler.verifierEcheances(); return "Exécuté"; }));
    }

    private void enregistrer(Tache t) { taches.put(t.code(), t); }

    /** Liste des tâches avec leur aperçu (si nettoyage) et leur dernière exécution connue. */
    public List<TacheVue> lister() {
        List<TacheVue> out = new ArrayList<>();
        for (Tache t : taches.values()) {
            Long apercu = t.apercu() == null ? null : t.apercu().getAsLong();
            TacheExecution derniere = executionRepository
                    .findTopByCodeOrderByStartedAtDesc(t.code()).orElse(null);
            out.add(new TacheVue(t.code(), t.libelle(), t.description(), t.nettoyage(), apercu,
                    derniere == null ? null : derniere.getStatut(),
                    derniere == null ? null : derniere.getFinishedAt(),
                    derniere == null ? null : derniere.getMessage()));
        }
        return out;
    }

    /** Exécute une tâche à la demande, journalise et renvoie le résultat. */
    public TacheVue executer(String code, UUID declenchePar) {
        Tache t = taches.get(code);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche inconnue : " + code);

        TacheExecution exec = new TacheExecution();
        exec.setCode(code);
        exec.setStartedAt(LocalDateTime.now());
        exec.setDeclenchePar(declenchePar);
        String message;
        String statut;
        try {
            message = t.action().call();
            statut = "SUCCES";
        } catch (Exception e) {
            log.warn("Tâche de maintenance {} en échec : {}", code, e.getMessage());
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            statut = "ECHEC";
        }
        exec.setFinishedAt(LocalDateTime.now());
        exec.setStatut(statut);
        exec.setMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
        executionRepository.save(exec);

        Long apercu = t.apercu() == null ? null : t.apercu().getAsLong();
        return new TacheVue(t.code(), t.libelle(), t.description(), t.nettoyage(), apercu,
                exec.getStatut(), exec.getFinishedAt(), exec.getMessage());
    }

    /** Vue d'une tâche pour l'API (métadonnées + aperçu + dernière exécution). */
    public record TacheVue(String code, String libelle, String description, boolean nettoyage,
                           Long apercu, String dernierStatut, LocalDateTime derniereExecution,
                           String dernierMessage) {}
}
