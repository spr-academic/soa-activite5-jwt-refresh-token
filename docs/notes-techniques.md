# Notes techniques

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

## Configuration JAX-RS et JSON

`RestActivator` enregistre explicitement les ressources, le filtre et les providers pour obtenir le même comportement au lancement et dans les tests. `JsonMapperProvider` conserve la relation option dans le JSON sans modifier son exclusion de la vue XML. Une seule API `javax.inject` est incluse dans le WAR.
