# Remise — SOA, Activité 5

**Sayed Touati — ESPRIT — Année universitaire 2026–2027**

**Atelier REST n°5 : JWT et Refresh Token**

Dépôt : https://github.com/spr-academic/soa-activite5-jwt-refresh-token

## Contenu remis

Le dépôt contient le projet Maven, les six classes demandées par le sujet, les ressources métier protégées, les tests HTTP, une collection Postman et le compte rendu de validation.

L’archive de remise contient aussi le WAR dans `dist/`. Les fichiers du sujet et les archives pédagogiques restent dans le dossier de cours local.

## Vérification

1. Ouvrir `pom.xml` dans IntelliJ avec JDK 21 ou utiliser un terminal à la racine du projet.
2. Exécuter `mvn clean verify` : 19 tests réussis sont attendus.
3. Exécuter `mvn jetty:run-war`.
4. Importer `requests/soa-activite5.postman_collection.json` dans Postman.
5. Lancer la collection dans l’ordre : refus sans jeton, connexion, accès autorisé, rotation, refus du rejeu, refus du mauvais type de jeton, déconnexion et refus du jeton révoqué.

La collection mémorise automatiquement les nouveaux jetons dans ses variables. Le fichier remis ne contient aucun jeton actif. Redémarrer le service avant une nouvelle démonstration rétablit les données initiales.

## Correspondance avec le sujet

| Partie | Réalisation |
|---|---|
| A | `JwtService`, `Credentials`, `AuthenticationEndPoint` ; signature HS512 ; access token de 15 minutes |
| B | `filter.Secured`, `filter.AuthenticationFilter` ; annotation sur les deux classes ressources ; refus `401` |
| C | `TokenResponse` ; refresh token de 7 jours ; registre en mémoire ; rotation et révocation ; `expiresIn` en secondes |

L’authentification reste la simulation décrite dans le sujet : elle vérifie uniquement que les deux champs sont non vides. La déconnexion révoque le refresh token ; l’access token reste valide jusqu’à expiration. La clé de signature est générée au déploiement ou fournie par configuration privée.

## Expiration dans Postman

Le test automatisé couvre l’expiration avec un jeton déjà expiré. Pour reproduire visuellement l’étape C4.3 du PDF, modifier temporairement `ACCESS_MINUTES` à `1`, redémarrer, se reconnecter, attendre plus d’une minute et vérifier `401` sur `/options`. Utiliser ensuite le refresh token pour obtenir un nouveau couple. Remettre `ACCESS_MINUTES` à `15` avant la remise finale.

**Validation enregistrée le 4 novembre 2026 : 19 tests réussis, aucun échec.**
