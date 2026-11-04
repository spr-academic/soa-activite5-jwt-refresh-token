# SOA — Activité 5 : JWT et Refresh Token

**Sayed Touati — ESPRIT — Année universitaire 2026–2027**

Atelier REST n°5 : sécuriser la gestion des options et des étudiants avec un access token de **15 minutes** et un refresh token de **7 jours**.

## Exécution

Prérequis : **JDK 21** et **Maven 3.9+**. Vérifier le JDK avec `mvn -version`.

```bash
mvn clean verify
mvn jetty:run-war
```

La première commande compile, construit le WAR et exécute les **19 tests HTTP**. La seconde démarre l’application sur le port 8080. Arrêt : `Ctrl+C`.

URL de base : `http://localhost:8080/soa-activite5-jwt-refresh-token/rest`

Dans IntelliJ, ouvrir `pom.xml` comme projet Maven avec JDK 21. Le WAR peut aussi être déployé sur Tomcat 9 (`javax.servlet`).

## Fonctionnement

| Appel | Résultat |
|---|---|
| `POST /authentication` avec JSON `username`, `password` | Couple de jetons, `tokenType: "Bearer"`, `expiresIn: 900` secondes |
| Ressources `/options` et `/etudiants` | Access token requis dans `Authorization: Bearer <jeton>` ; sinon `401` |
| `POST /authentication/refresh` avec formulaire `refreshToken` | Nouveau couple ; ancien refresh token consommé ; rejeu refusé avec `401` |
| `POST /authentication/logout` avec formulaire `refreshToken` | Révocation du refresh token et réponse `204` |

Comme dans le sujet, l’authentification vérifie uniquement que les deux champs sont non vides. `I001` / `pwd001` est un exemple, sans vérification dans une base d’utilisateurs. Après déconnexion, l’access token reste valide jusqu’à expiration.

La clé de signature est générée au déploiement. Une clé privée persistante peut être fournie par `SOA_JWT_SECRET` ; elle ne doit pas être enregistrée dans Git. Les données et le registre des refresh tokens sont en mémoire.

## Démonstration et résultats

Importer [la collection Postman](requests/soa-activite5.postman_collection.json) et exécuter les requêtes dans l’ordre. La collection mémorise les jetons reçus et couvre la connexion, l’accès protégé, la rotation, le refus du rejeu et la déconnexion. Des exemples simples sont aussi disponibles dans [atelier.http](requests/atelier.http).

- [Validation : 19 tests réussis, aucun échec](docs/validation.md).
- [Guide de remise et test d’expiration](docs/remise.md).
- [Notes techniques et adaptations du projet fourni](docs/notes-techniques.md).

La logique JWT est dans `utilities/JwtService.java`, le filtre et l’annotation dans `filter/`, les trois points d’accès dans `ressources/AuthenticationEndPoint.java`. Ces chemins sont relatifs à `src/main/java/`.
