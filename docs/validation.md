# Validation — Activité 5

**Date : 4 novembre 2026**  
**Résultat : WAR construit ; 19 tests réussis ; 0 échec, 0 erreur, 0 test ignoré.**

## Environnement et méthode

- Eclipse Temurin JDK 21.0.13 ; Maven 3.9.11 ; compilation Java 17.
- Jersey 2.27 ; JJWT 0.6.0 ; Jetty 9.4.57.v20241219.
- Commande finale : `mvn -o clean verify`, après téléchargement des dépendances.
- Le WAR est déployé dans un vrai serveur Jetty sur une interface locale et un port disponible choisi automatiquement.
- JAX-RS est activé par `RestActivator` et `@ApplicationPath("rest")`. Aucun mappage manuel ne remplace cette activation dans le test.
- Une clé éphémère est générée par le test et transmise par propriété JVM avant le déploiement ; aucune clé de test fixe n’est publiée.

## Couverture

| Domaine | Contrôles réalisés |
|---|---|
| Connexion | Couple de jetons, `expiresIn = 900`, type Bearer, absence de cache |
| Claims | Sujet, émetteur, identifiant unique, type, durées de 900 et 604800 secondes |
| Entrées invalides | Champs absents ou vides : `403` ; JSON mal formé : `400` |
| Protection | Toutes les routes des deux ressources refusent les appels sans Bearer, y compris les vues XML |
| Requêtes autorisées | Lecture des options et étudiants ; créations `201` ; données conservées entre requêtes |
| Access token | Expiration, mauvaise signature, mauvais émetteur et en-tête invalide : `401` |
| Séparation des usages | Refresh token refusé comme access token ; access token refusé au rafraîchissement |
| Rotation | Nouveau couple accepté ; ancien refresh token refusé ; nouvelle rotation possible |
| Registre | Refresh token inconnu, expiré, vide ou mal formé refusé |
| Concurrence | Deux appels simultanés au même refresh token : exactement un `200` et un `401` |
| Déconnexion | `204` sans corps ; refresh token révoqué ; déconnexion répétée sans erreur ; access token encore valide |

Le test d’expiration utilise un JWT déjà expiré, correctement signé avec la clé éphémère du test, puis l’envoie au filtre via HTTP. Il ne réduit pas la durée des jetons de l’application et n’attend pas quinze minutes.

Le [rapport XML](rapport-tests.xml) contient les résultats individuels. Les propriétés de l’environnement et les journaux serveur sont omis de cette copie ; les rapports complets sont régénérés dans `target/failsafe-reports/`.

La collection Postman est fournie pour reproduire le scénario de démonstration. Elle n’a pas été exécutée dans l’interface Postman lors de cette validation. L’import graphique dans IntelliJ et le déploiement dans Tomcat n’ont pas été exécutés.

## Vérification du lancement

Le 4 novembre 2026, le scénario a aussi été exécuté sur le service lancé avec `mvn jetty:run-war` : refus sans jeton, connexion, accès autorisé, rotation, refus du rejeu, déconnexion et refus du refresh token révoqué. Les 19 tests couvrent aussi la lecture XML autorisée, sans objet option dans cette représentation.
