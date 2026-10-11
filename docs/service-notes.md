# Note technique — Atelier 4 : IoC, DI et couche Service

## A. Modes d'injection comparés

| Critère | Constructeur (retenu) | Attribut (`@Autowired` sur le champ) |
|---|---|---|
| Champ `final` possible | Oui | Non |
| Dépendance visible dans la signature de la classe | Oui (paramètre du constructeur) | Non (cachée dans le corps) |
| Utilisable avec `new` hors conteneur sans dépendance `null` | Oui : on fournit le repository au constructeur | Non : le champ reste `null` tant que Spring n'injecte pas |
| Signalé par SonarQube for IDE | Non | Oui (règle S6813, à vérifier dans le panneau) |

Convention AutoLoc : injection par constructeur, champs `final`, dépendance typée par une interface.
Avec `@RequiredArgsConstructor`, le service ne peut jamais exister dans un état incomplet.

## B. Services

| Service | Dépendances injectées | Mode | Justification |
|---|---|---|---|
| AgenceServiceImpl | IAgenceRepository | Constructeur | Une seule dépendance, obligatoire, `final` ; refuse nom, ville ou adresse vides. |
| EmployeServiceImpl | IEmployeRepository | Constructeur | Idem ; refuse nom, prénom vides ou rôle absent. |
| VehiculeServiceImpl | IVehiculeRepository | Constructeur | Idem ; refuse une immatriculation vide ou un tarif journalier négatif. |
| EquipementServiceImpl | IEquipementRepository | Constructeur | Idem ; refuse un libellé vide. |
| ClientServiceImpl | IClientRepository | Constructeur | Idem ; refuse nom, prénom ou e-mail vides. |
| ReservationServiceImpl | IReservationRepository | Constructeur | Idem ; refuse une date de fin antérieure à la date de début. |
| ContratServiceImpl | IContratRepository | Constructeur | Idem ; refuse un montant total négatif ; la suppression propage aux paiements par cascade. |
| PaiementServiceImpl | IPaiementRepository | Constructeur | Lecture seule (`findById`, `findAll`) : les paiements passent par le Contrat (composition). |
| MaintenanceServiceImpl | IMaintenanceRepository | Constructeur | Idem ; refuse une date de fin antérieure à la date de début (le modèle n'a pas d'attribut coût). |

Tous les services sont des singletons sans état : aucun attribut modifiable, aucun `new` de repository.
`update` copie les attributs simples ; les associations (agence, client, véhicule…) ne sont pas modifiées ici, leur gestion relève du chapitre 9.

## C. Messages d'erreur du conteneur

**Message 1 — `NoSuchBeanDefinitionException` (bean `IContratService` introuvable).**
Bean concerné : `IContratService`, demandé par le constructeur de `ContratController`.
Causes les plus probables côté `ContratServiceImpl` : (1) l'annotation `@Service` a été oubliée ; (2) la classe est dans un package hors de `tn.esprit.autoloc`, donc jamais détectée par le scan.
Correction : ajouter `@Service`, ou déplacer la classe dans `tn.esprit.autoloc.service`.

**Message 2 — `NoUniqueBeanDefinitionException` (2 beans : `emailNotificateur`, `smsNotificateur`).**
Cause : deux beans du même type, Spring ne sait pas lequel injecter dans `NotificationController`.
Corrections :
1. `@Primary` sur la **classe** du bean à utiliser par défaut (par exemple `EmailNotificateur`).
2. `@Qualifier("smsNotificateur")` sur le **paramètre du constructeur** du contrôleur pour choisir explicitement.

**Message 3 — dépendance circulaire (`clientServiceImpl` ↔ `reservationServiceImpl`).**
Avec l'injection par constructeur, chaque bean a besoin de l'autre pour être **construit** : aucun des deux ne peut être instancié le premier, et on ne peut pas créer un objet à moitié construit comme avec un setter ou un attribut. Le cycle est donc impossible à contourner, ce qui est voulu : il signale un défaut de conception.
Piste : extraire la responsabilité commune dans un troisième service dont les deux dépendent, ou faire passer l'un des services par le repository de l'autre au lieu du service.

## D. Anomalies SonarQube for IDE

| Règle | Explication | Correction |
|---|---|---|
| S6813 (injection par attribut) | `@Autowired` sur un champ : champ non `final`, dépendance cachée, `null` hors conteneur. | Retour à `@RequiredArgsConstructor` et au champ `final`. |
| S112 (exception générique) | `RuntimeException` ne dit pas ce qui s'est passé et empêche un traitement ciblé. | Retour à `ResourceNotFoundException`, exception métier explicite. |
| S1128 (import inutile) | Un import non utilisé encombre le fichier et peut masquer les vraies dépendances de la classe. | Suppression de l'import. |

Les deux premières anomalies proviennent des expériences de l'énoncé (étapes 2 et 4) ; la troisième a été relevée en ajoutant temporairement un import inutile. Après correction, l'analyse des packages `service` et `exception` ne signale plus aucune anomalie.

## E. Questions de compréhension

1. **Où est le `new` de `ContratServiceImpl` et qui l'exécute ?** Il est exécuté par le conteneur Spring au démarrage (`SpringApplication.run`), qui détecte `@Service` par le scan et appelle le constructeur en lui fournissant le proxy du repository. Aucun `new` n'est écrit dans notre code.
2. **Pourquoi dépendre de `IContratService` et non de `ContratServiceImpl` ?** Le contrôleur dépend d'une abstraction : on peut changer ou remplacer l'implémentation (ou fournir une doublure en test) sans modifier le contrôleur. Cela limite le couplage.
3. **Pourquoi un service singleton doit-il rester sans état ?** L'instance est partagée par tous les threads. Exemple de bug : un attribut `contratCourant` modifié par une requête puis lu par une autre ; deux utilisateurs simultanés se retrouvent à manipuler le contrat de l'autre.
4. **Pourquoi `update` charge-t-il l'existant avant `save` ?** Passer directement l'objet reçu écraserait toutes les colonnes (y compris celles non envoyées) et, avec `orphanRemoval`, pourrait vider la collection de paiements. On charge, on recopie les attributs modifiables, puis on sauvegarde.
5. **`@Component` vs `@Bean` ; `@Primary` vs `@Qualifier` ; `IPaiementService` en lecture seule.**
    - `@Component` se pose sur une classe qu'on écrit et qui est détectée par le scan ; `@Bean` se pose sur une méthode de configuration pour déclarer un bean explicitement (classe de bibliothèque, construction particulière).
    - `@Primary` (sur la classe) désigne le candidat par défaut ; `@Qualifier` (à l'injection) choisit un bean précis par son nom.
    - Un paiement n'existe que rattaché à son contrat (composition) : sa création, sa modification et sa suppression passent par le Contrat (cascade et `orphanRemoval`) ; le service Paiement n'expose donc que la lecture.
