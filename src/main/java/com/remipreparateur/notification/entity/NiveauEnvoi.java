package com.remipreparateur.notification.entity;

/**
 * Droit d'émission d'un joueur :
 *  AUCUN  = ne peut pas envoyer ;
 *  EQUIPE = peut envoyer à toute l'équipe ;
 *  CIBLE  = peut envoyer à des joueurs ciblés (inclut aussi l'équipe).
 */
public enum NiveauEnvoi { AUCUN, EQUIPE, CIBLE }
