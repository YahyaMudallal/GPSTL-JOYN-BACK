# Joyn Backend
Dépôt du backend de l'application Joyn.

## Prérequis
* **JDK :** OpenJDK 21
* **Maven :** inclus via le wrapper (`./mvnw`)
* **Base de données :** PostgreSQL

## Installation et Lancement de PostgreSQL en local

### 1. Linux / Ubuntu / WSL
```bash
# Installer PostgreSQL
sudo apt update
sudo apt install postgresql postgresql-contrib

# Démarrer le service
sudo service postgresql start

# Créer l'utilisateur et la base de données
sudo -u postgres psql -c "CREATE USER joyn_user WITH PASSWORD 'joyn_secret';"
sudo -u postgres psql -c "CREATE DATABASE joyn_db OWNER joyn_user;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE joyn_db TO joyn_user;"
```

#### 2. Windows
1. Téléchargez et lancez l'installateur officiel : [PostgreSQL Windows Installer](https://www.postgresql.org/download/windows/).
2. Définissez le mot de passe de l'utilisateur `postgres`.
3. Ouvrez **pgAdmin** ou le terminal **SQL Shell (psql)** et exécutez :
   ```sql
   CREATE DATABASE joyn_db;
   CREATE USER joyn_user WITH ENCRYPTED PASSWORD 'joyn_secret';
   GRANT ALL PRIVILEGES ON DATABASE joyn_db TO joyn_user;
   ```
4. Le service Windows démarre automatiquement en tâche de fond sur le port `5432`.

#### 3. macOS (Homebrew)
```bash
# Installer et lancer le service
brew install postgresql@16
brew services start postgresql@16

# Créer la base et les droits
psql postgres -c "CREATE DATABASE joyn_db;"
psql postgres -c "CREATE USER joyn_user WITH ENCRYPTED PASSWORD 'joyn_secret';"
psql postgres -c "GRANT ALL PRIVILEGES ON DATABASE joyn_db TO joyn_user;"
```

## Installation et Configuration du projet

1. **Cloner le dépôt :**
```bash
git clone https://github.com/votre-orga/joyn-backend.git
cd joyn-backend
```

### 1. Fichier de configuration par défaut (`application.properties`)

Le fichier `src/main/resources/application.properties` est versionné sur Git et contient les valeurs standards prêtes à l'emploi :

```properties
spring.application.name=joyn
server.port=${PORT:8080}

# Valeurs par défaut pour le développement local
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/joyn_db}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
```
Si ta machine utilise ces identifiants standards, l'application démarre directement sans configuration supplémentaire.

### 2. Personnalisation locale (application-local.properties)

Si tu souhaites surcharger ces valeurs (mot de passe Postgres différent, port alternatif, etc.) sans risquer de commiter tes identifiants :

1. Créer le fichier local :
Copie le modèle d'exemple présent dans le dépôt :
```Bash
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```
2.Adapter tes accès :
Modifie les propriétés souhaitées dans `src/main/resources/application-local.properties`. Ce fichier est ignoré par Git via le .gitignore.

3.Activer le profil local :
En ligne de commande :
```Bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
(ou sous Windows : .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local)
Dans Eclipse / IDE :
Ouvre Run Configurations... > sélectionne ta configuration Java ou Spring Boot > onglet Arguments > ajoute dans VM arguments :
```Plaintext
-Dspring.profiles.active=local
```
⚠️ Sécurité : Ne commitez jamais de mots de passe de production ou de clés d'API. Vérifiez que application-local.properties figure bien dans votre .gitignore.

---

## Lancé le serveur en local
**Linux / MacOS**
```bash
./mvnw spring-boot:run
```
**Window**
```shell
.\mvnw.cmd spring-boot:run
```

**Via l'IDE**
Exécutez la méthode principale (main) de la classe :
`src/main/java/com/gpstl/joyn/JoynApplication.java`

L'application démarre par défaut sur le port 8080 : http://localhost:8080.

## Méthode de travail

Le respect strict de ces conventions est indispensable pour éviter les conflits et les régressions :

1. Gestion des branches
- Interdiction stricte de pousser directement sur la branche main.
- Créez une branche dédiée par tâche depuis main à jour :
```bash
git checkout main
git pull
git checkout -b <type>/<nom-de-fonctionnalite>
```
- Conventions de nommage des branches :
    - feat/<nom> : Nouvelle fonctionnalité
    - fix/<nom> : Correction de bug
    - test/<nom> : Ajout ou correction de tests
    - refactor/<nom> : Refactorisation sans modification fonctionnelle

2. Format des commits
- Rédigez des messages clairs et concis :
    - feat: ajout de la route de connexion POST /auth/login
    - fix: gestion du statut 404 si l'utilisateur n'existe pas
    - test: tests unitaires sur le calcul du panier

3. Tests obligatoires
- Chaque nouvelle classe de logique métier (@Service) ou contrôleur (@RestController) doit obligatoirement être accompagnée de tests unitaires ou d'intégration dans src/test/java/.

4. Processus de Pull Request (PR) & Revue de code
- Poussez votre branche sur GitHub :
```bash
git push origin <votre-branche>
```
- Ouvrez une Pull Request vers la branche main.
- Remplissez la description du template de PR (ce qui a été fait, comment tester).
- Validation obligatoire : La PR nécessite au moins une approbation (review approval) d'un autre membre de l'équipe avant de pouvoir être fusionnée.
- Résolvez les éventuels conflits en local via un rebase ou un merge depuis main avant de finaliser.