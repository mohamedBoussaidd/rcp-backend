package com.remipreparateur.plateforme.service;

import com.remipreparateur.notification.repository.NotificationRepository;
import com.remipreparateur.notification.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nettoyages de cohérence (hors rétention par ancienneté) : abonnements Web Push fantômes et
 * notifications orphelines. Chaque opération est transactionnelle et renvoie un compteur ; un
 * comptage préalable permet d'afficher le volume avant action.
 */
@Service
public class NettoyageService {

    private final PushSubscriptionRepository pushRepository;
    private final NotificationRepository notificationRepository;

    public NettoyageService(PushSubscriptionRepository pushRepository,
                            NotificationRepository notificationRepository) {
        this.pushRepository = pushRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public long compterPushOrphelins() {
        return pushRepository.compterOrphelins();
    }

    @Transactional
    public int purgerPushOrphelins() {
        return pushRepository.purgerOrphelins();
    }

    @Transactional(readOnly = true)
    public long compterNotifsOrphelines() {
        return notificationRepository.compterOrphelinesSansDestinataire();
    }

    @Transactional
    public int purgerNotifsOrphelines() {
        return notificationRepository.purgerOrphelinesSansDestinataire();
    }
}
