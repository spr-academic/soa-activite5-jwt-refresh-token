# SOA — Activité 5 : JWT et Refresh Token

**ESPRIT · Année universitaire 2026–2027**  
**Étudiant : Sayed Touati**  
**Atelier REST n°5 — Sécuriser un service web RESTful**

Application JAX-RS de gestion des options et des étudiants, sécurisée par un access token de **15 minutes** et un refresh token de **7 jours**. La connexion délivre les deux jetons ; le rafraîchissement remplace le couple et consomme l’ancien refresh token. La déconnexion révoque le refresh token.

**Validation du 4 novembre 2026 : 19 tests HTTP réussis, aucun échec.** Voir le [compte rendu](docs/validation.md) et le [guide de remise](docs/remise.md).

## Démarrer

Prérequis : **JDK 21** et **Maven 3.9+**. Le code est compilé pour Java 17, comme dans le projet de départ. Les dépendances sont téléchargées au premier lancement.

```bash
mvn clean verify
mvn jetty:run-war
```

La première commande construit le WAR et lance les tests d’intégration. La seconde démarre le service sur `127.0.0.1:8080`. Arrêt : `Ctrl+C`.

**URL de base :** `http://localhost:8080/soa-activite5-jwt-refresh-token/rest`

Dans IntelliJ, ouvrir `pom.xml` comme projet Maven et sélectionner JDK 21 pour le projet et Maven. Le WAR `target/soa-activite5-jwt-refresh-token.war` peut aussi être déployé sur Tomcat 9 ou un conteneur compatible `javax.servlet`.

## Scénario Postman

Importer [la collection](requests/soa-activite5.postman_collection.json), puis exécuter les requêtes dans l’ordre ou utiliser le Collection Runner. La variable `baseUrl` est déjà renseignée. Les scripts de la collection mémorisent les jetons reçus pendant l’exécution ; le fichier fourni ne contient aucun jeton préenregistré.

1. Appeler les options sans jeton : `401`.
2. Se connecter avec `I001` et `pwd001` : `200`, deux jetons et `expiresIn: 900`.
3. Lire les options, créer une option et lire les étudiants avec l’access token : `200` ou `201` à la création.
4. Rafraîchir avec le refresh token : `200`, nouveau couple de jetons.
5. Utiliser le nouvel access token : `200`.
6. Réutiliser l’ancien refresh token : `401`.
7. Envoyer un refresh token comme Bearer sur une ressource : `401`.
8. Se déconnecter : `204`, puis tenter le rafraîchissement avec le jeton révoqué : `401`.
9. Vérifier que l’access token reste utilisable jusqu’à son expiration.

La collection contient des assertions sur les statuts et le contenu du couple de jetons. Elle est fournie pour l’exécution manuelle ; la validation enregistrée dans ce dépôt provient des tests Java sur le WAR.

## Points d’accès d’authentification

| Méthode et chemin | Corps | Réponse |
|---|---|---|
| `POST /authentication` | JSON : `username`, `password` | `200` et `TokenResponse` ; `403` si champs absents ou vides |
| `POST /authentication/refresh` | Formulaire `refreshToken` | `200` et nouveau couple ; `401` si invalide, expiré, inconnu ou déjà consommé |
| `POST /authentication/logout` | Formulaire `refreshToken` | `204`, y compris si le jeton est déjà invalide |

La réponse de connexion et de rafraîchissement contient `accessToken`, `refreshToken`, `tokenType: "Bearer"` et `expiresIn: 900` (secondes). Ces réponses utilisent `Cache-Control: no-store`.

Les ressources `OptionRessources` et `EtudiantRessources` portent `@Secured` au niveau de la classe. Toutes leurs méthodes exigent `Authorization: Bearer <accessToken>`. Les trois points d’accès d’authentification restent publics pour permettre le renouvellement après expiration.

## Organisation

| Emplacement | Rôle |
|---|---|
| `utilities/JwtService.java` | Signature HS512, validation, rotation atomique et révocation |
| `filter/Secured.java` | Annotation de liaison JAX-RS |
| `filter/AuthenticationFilter.java` | Vérification du Bearer avant l’appel de la ressource |
| `ressources/AuthenticationEndPoint.java` | Connexion, rafraîchissement et déconnexion |
| `entities/Credentials.java`, `entities/TokenResponse.java` | Corps de connexion et réponse JSON |
| `ressources/OptionRessources.java`, `ressources/EtudiantRessources.java` | Ressources métier fournies et protégées |
| `src/test/java/integration/RestApiIT.java` | Tests HTTP sur le WAR déployé dans Jetty |
| `requests/` | Collection Postman et exemples HTTP |

Les chemins Java de ce tableau sont relatifs à `src/main/java/`, sauf indication contraire.

## Choix et limites de l’atelier

- Le projet part de l’archive étudiante fournie. L’énoncé et la correction fournie ont servi de références ; les archives et le sujet restent dans le dossier pédagogique local.
- L’authentification reprend volontairement celle du sujet : tout login et mot de passe non vides sont acceptés. `I001` / `pwd001` constitue un exemple, et non un compte vérifié en base. Aucun annuaire d’utilisateurs ni stockage de mots de passe n’est implémenté.
- Aucune clé de signature fixe n’est publiée. Sans configuration, une clé aléatoire privée de 64 octets est générée au déploiement. Une clé persistante peut être fournie par `SOA_JWT_SECRET` ou la propriété JVM `soa.jwt.secret`, avec au moins 64 octets UTF-8. Ne pas enregistrer cette valeur dans Git.
- Le registre des refresh tokens et les données métier sont en mémoire. Un redéploiement vide le registre ; avec la clé aléatoire par défaut, il invalide également les anciens access tokens.
- Le contrôle vérifie la signature, l’expiration, l’algorithme HS512, l’émetteur, le type de jeton et les claims requis. Le retrait atomique du `jti` empêche deux rafraîchissements concurrents d’utiliser le même refresh token.
- L’access token reste valide après déconnexion, jusqu’à son expiration, conformément au sujet.
- `expiresIn` est exprimé en secondes : la valeur `15` du fichier de correction a été corrigée en `900`, conformément au PDF.
- Les listes métier sont initialisées une seule fois pour éviter leur remise à zéro lors de la construction d’une ressource. Les URI et statuts métier du starter sont conservés, dont `201` pour une création.
- Jersey reste en **2.27** et JJWT en **0.6.0**, versions du cours. Les modules Jersey sont alignés ; JAXB est explicite pour JDK 21. Une API d’injection dupliquée est exclue du WAR. Le JSON mal formé renvoie `400`.

## Analyser un JWT

Un JWT signé possède trois segments Base64URL : en-tête, claims et signature. Décoder les deux premiers permet de lire `alg`, `sub`, `iss`, `iat`, `exp`, `type` et `jti` ; cela ne vérifie pas la signature. Ces jetons sont signés et non chiffrés. Aucune compression n’est activée dans cet atelier.

L’API de signature et de validation utilisée est décrite dans la [documentation JJWT 0.6.0](https://github.com/jwtk/jjwt/tree/0.6.0).
