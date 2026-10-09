# Stratégie de fetch et de cascade — AutoLoc

Règles générales : toutes les associations sont en `FetchType.LAZY` (y compris les `@ManyToOne`
et `@OneToOne`, EAGER par défaut) pour ne jamais charger une chaîne d'objets inutile.
Une cascade n'est posée que lorsque l'enfant ne peut pas vivre sans son parent (composition).
`@Data` n'est pas utilisé (risque de `StackOverflowError` sur les associations bidirectionnelles).

| Association | Côté propriétaire (FK) | Fetch | Cascade | Justification |
|---|---|---|---|---|
| Contrat → Paiement | Paiement (`contrat_id_contrat`) | LAZY | ALL + orphanRemoval | Un paiement n'existe que rattaché à son contrat (composition) : supprimer le contrat supprime ses paiements, et retirer un paiement de la liste le supprime en base. |
| Agence → Vehicule | Vehicule (`agence_id_agence`) | LAZY | Aucune | Un véhicule survit à la fermeture de son agence ; il peut être réaffecté. La liste n'est chargée que sur demande. |
| Agence → Employe | Employe (`agence_id_agence`) | LAZY | Aucune | Un employé est une entité autonome (réaffectation possible) ; supprimer une agence ne doit pas supprimer le personnel en cascade. |
| Vehicule ↔ Equipement | Vehicule (table `vehicule_equipement`) | LAZY | Aucune | Les équipements (GPS, siège bébé…) sont partagés entre véhicules : supprimer un véhicule ne doit pas supprimer un équipement utilisé ailleurs. `Set` pour éviter les doublons et les requêtes de suppression/réinsertion propres aux `List` sur un ManyToMany. |
| Client → Reservation | Reservation (`client_id_client`) | LAZY | PERSIST | Enregistrer un nouveau client avec ses premières réservations les persiste ensemble. Pas de REMOVE : l'historique de réservations ne doit pas disparaître avec une suppression accidentelle du client. |
| Reservation → Vehicule | Reservation (`vehicule_id_vehicule`) | LAZY | Aucune | Le véhicule a un cycle de vie indépendant de la réservation ; association unidirectionnelle suffisante. |
| Reservation ↔ Contrat | Contrat (`reservation_id_reservation`) | LAZY | ALL côté Reservation | Un contrat n'a de sens que pour sa réservation : sauvegarde, mise à jour et suppression se propagent. Remarque : côté inverse (`mappedBy`), Hibernate doit lire la table `contrat` pour savoir s'il y a un contrat ; le LAZY n'y est donc effectif que côté propriétaire (sauf bytecode enhancement). |
| Vehicule → Maintenance | Maintenance (`vehicule_id_vehicule`) | LAZY | PERSIST | Une maintenance planifiée peut être enregistrée avec le véhicule. Pas de REMOVE : l'historique d'entretien est conservé. |

## Remarques

- Colonnes de clé étrangère : nommage par défaut d'Hibernate (`<attribut>_<pk référencée>`), sans `@JoinColumn`.
- LAZY : tout accès à une collection hors transaction lève une `LazyInitializationException` ; on le traitera par des requêtes dédiées (Atelier 7).
- Méthodes `addPaiement` / `removePaiement` dans `Contrat` : elles gardent les deux côtés synchronisés.
