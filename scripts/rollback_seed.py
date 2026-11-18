#!/usr/bin/env python3
"""
Script de rollback / nettoyage des données de test injectées par seed_test_data.py :
- Supprime les candidatures, entretiens, conversations et messages de test
- Supprime les offres d'emploi générées pour les entreprises de test
- Supprime les recruteurs et leurs profils
- Supprime les entreprises de test (listées dans data/companies_jobs.json)
- Supprime les candidats de test (listés dans data/candidates.json ou avec email @joyn.test)
- Nettoie les fichiers médias physiques (dossiers media/ ou objets Supabase S3)
- Préserve les comptes administrateurs et autres données réelles
"""

import os
import sys
import json
import shutil
import argparse
from pathlib import Path
from urllib.parse import urlparse

import psycopg2

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
COMPANIES_FILE = DATA_DIR / "companies_jobs.json"
CANDIDATES_FILE = DATA_DIR / "candidates.json"

MEDIA_DIR = BASE_DIR / "media"
RESUME_DIR = MEDIA_DIR / "candidate-resumes" / "resumes"
COMPANY_LOGOS_DIR = MEDIA_DIR / "company-logos" / "company_logos"
PROFILE_PICTURES_DIR = MEDIA_DIR / "profile-pictures" / "profile_pictures"

def load_dotenv_file():
    for env_name in [".env", ".env.local"]:
        env_file = BASE_DIR / env_name
        if env_file.is_file():
            with env_file.open("r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, val = line.split("=", 1)
                    key = key.strip()
                    val = val.strip().strip("'\"")
                    if key not in os.environ and val != "":
                        os.environ[key] = val

load_dotenv_file()

def parse_db_url(url: str, user: str = None, password: str = None) -> dict:
    if not url:
        return {}
    clean_url = url.strip()
    if clean_url.startswith("jdbc:"):
        clean_url = clean_url[5:]
    if not clean_url.startswith("postgresql://") and not clean_url.startswith("postgres://"):
        clean_url = "postgresql://" + clean_url
    parsed = urlparse(clean_url)
    config = {}
    if parsed.path and len(parsed.path) > 1:
        config["dbname"] = parsed.path[1:].split("?")[0]
    if parsed.hostname:
        config["host"] = parsed.hostname
    if parsed.port:
        config["port"] = parsed.port
    if user:
        config["user"] = user
    elif parsed.username:
        config["user"] = parsed.username
    if password:
        config["password"] = password
    elif parsed.password:
        config["password"] = parsed.password
    return config

def get_db_connection_params() -> dict:
    db_url = os.getenv("DB_URL")
    db_user = os.getenv("DB_USER")
    db_password = os.getenv("DB_PASSWORD")
    if db_url:
        params = parse_db_url(db_url, user=db_user, password=db_password)
        if "dbname" not in params:
            params["dbname"] = os.getenv("DB_NAME", "joyn_db")
        if "user" not in params:
            params["user"] = db_user or "joyn_user"
        if "password" not in params:
            params["password"] = db_password or "joyn_secret"
        if "host" not in params:
            params["host"] = os.getenv("DB_HOST", "localhost")
        if "port" not in params:
            params["port"] = int(os.getenv("DB_PORT", 5432))
        return params
    return {
        "dbname": os.getenv("DB_NAME", "joyn_db"),
        "user": db_user or os.getenv("DB_USER", "joyn_user"),
        "password": db_password or os.getenv("DB_PASSWORD", "joyn_secret"),
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", 5432))
    }

class MediaCleaner:
    def __init__(self, mode="local"):
        self.mode = mode.lower()
        self.s3_client = None
        self.bucket = os.getenv("SUPABASE_S3_BUCKET") or "joyn_file_bucket"
        if self.mode in ("supabase", "both"):
            self._init_s3()

    def _init_s3(self):
        try:
            import boto3
            from botocore.config import Config
        except ImportError:
            print("[ATTENTION] boto3 non installé, suppression Supabase S3 ignorée.")
            return

        endpoint = os.getenv("SUPABASE_S3_ENDPOINT")
        access_key = os.getenv("SUPABASE_S3_ACCESS_KEY")
        secret_key = os.getenv("SUPABASE_S3_SECRET_KEY")
        region = os.getenv("SUPABASE_S3_REGION") or "eu-west-3"

        if endpoint and access_key and secret_key:
            self.s3_client = boto3.client(
                's3',
                endpoint_url=endpoint,
                aws_access_key_id=access_key,
                aws_secret_access_key=secret_key,
                region_name=region,
                config=Config(s3={'addressing_style': 'path'})
            )

    def delete_local_folder(self, folder: Path):
        if folder.is_dir():
            try:
                shutil.rmtree(folder)
            except Exception:
                pass

    def delete_s3_prefix(self, prefix: str) -> int:
        if not self.s3_client:
            return 0
        deleted_count = 0
        try:
            paginator = self.s3_client.get_paginator('list_objects_v2')
            for page in paginator.paginate(Bucket=self.bucket, Prefix=prefix):
                items = page.get('Contents', [])
                for obj in items:
                    key = obj['Key']
                    try:
                        self.s3_client.delete_object(Bucket=self.bucket, Key=key)
                        deleted_count += 1
                    except Exception as err:
                        print(f"[ATTENTION] Échec suppression S3 pour {key} : {err}")
        except Exception as e:
            print(f"[ATTENTION] Échec du listage S3 pour {prefix} : {e}")
        return deleted_count

    def get_s3_subfolder_ids(self, prefix: str) -> set:
        if not self.s3_client:
            return set()
        subfolders = set()
        try:
            paginator = self.s3_client.get_paginator('list_objects_v2')
            for page in paginator.paginate(Bucket=self.bucket, Prefix=prefix, Delimiter='/'):
                for cp in page.get('CommonPrefixes', []):
                    folder_name = cp.get('Prefix', '').rstrip('/').split('/')[-1]
                    try:
                        subfolders.add(int(folder_name))
                    except ValueError:
                        pass
        except Exception:
            pass
        return subfolders

def parse_args():
    parser = argparse.ArgumentParser(description="Rollback et nettoyage des données de test Joyn.")
    parser.add_argument(
        "--storage", "-s",
        choices=["local", "supabase", "both"],
        default=os.getenv("JOYN_STORAGE_PROVIDER", os.getenv("STORAGE_PROVIDER", "local")).lower(),
        help="Cible de nettoyage des médias : 'local', 'supabase' ou 'both'."
    )
    parser.add_argument(
        "--yes", "-y",
        action="store_true",
        help="Confirmer la suppression sans prompt interactif."
    )
    return parser.parse_args()

def main():
    args = parse_args()

    # Charger les noms d'entreprises et emails de test depuis les JSON
    company_names = []
    if COMPANIES_FILE.is_file():
        with COMPANIES_FILE.open("r", encoding="utf-8") as f:
            cdata = json.load(f)
            company_names = [c["name"] for c in cdata if "name" in c]

    candidate_emails = []
    if CANDIDATES_FILE.is_file():
        with CANDIDATES_FILE.open("r", encoding="utf-8") as f:
            cand_data = json.load(f)
            candidate_emails = [c["email"] for c in cand_data if "email" in c]

    print("="*75)
    print(" SCRIPT DE ROLLBACK / NETTOYAGE DES DONNÉES DE TEST JOYN")
    print("="*75)
    print(f"Cibles identifiées :")
    print(f"  - {len(company_names)} entreprises de test (et leurs recruteurs/offres)")
    print(f"  - {len(candidate_emails)} candidats de test (+ tous les comptes @joyn.test)")
    print(f"  - Nettoyage médias : {args.storage.upper()}")

    if not args.yes:
        confirm = input("\nÊtes-vous sûr de vouloir supprimer ces données de test ? (o/N) : ").strip().lower()
        if confirm not in ("o", "oui", "y", "yes"):
            print("Opération annulée.")
            sys.exit(0)

    db_params = get_db_connection_params()
    host_display = f"{db_params.get('host', 'localhost')}:{db_params.get('port', 5432)}/{db_params.get('dbname', 'joyn_db')}"
    print(f"\nConnexion à PostgreSQL ({host_display})...")
    try:
        conn = psycopg2.connect(**db_params)
        conn.autocommit = False
        cur = conn.cursor()
    except Exception as e:
        print(f"[ERREUR] Impossible de se connecter à PostgreSQL : {e}")
        sys.exit(1)

    cleaner = MediaCleaner(mode=args.storage)

    # 1. Identifier les IDs des entreprises de test
    cur.execute("""
        SELECT id, name FROM companies 
        WHERE name = ANY(%s) OR email_domain LIKE %s OR website LIKE %s;
    """, (company_names, '%.example', '%.example'))
    companies_to_delete = cur.fetchall()
    company_ids = [row[0] for row in companies_to_delete]
    print(f"-> {len(company_ids)} entreprises trouvées en BDD.")

    # 2. Identifier les utilisateurs de test (recruteurs + candidats)
    # ATTENTION : EXCLURE IMPÉRATIVEMENT les comptes administrateurs (ex: admin@joyn.test)
    cur.execute("""
        SELECT id, email, role FROM users 
        WHERE role != 'ADMIN' 
          AND email != 'admin@joyn.test'
          AND (email LIKE %s OR email = ANY(%s));
    """, ('%@joyn.test', candidate_emails))
    users_to_delete = cur.fetchall()
    user_ids = [row[0] for row in users_to_delete]
    candidate_user_ids = [row[0] for row in users_to_delete if row[2] == "CANDIDATE"]

    # Récupérer également les utilisateurs recruteurs liés aux entreprises de test
    if company_ids:
        cur.execute("SELECT user_id FROM recruiter_profiles WHERE company_id = ANY(%s);", (company_ids,))
        for row in cur.fetchall():
            ruid = row[0]
            if ruid and ruid not in user_ids:
                cur.execute("SELECT role FROM users WHERE id = %s;", (ruid,))
                res = cur.fetchone()
                if res and res[0] != 'ADMIN':
                    user_ids.append(ruid)

    print(f"-> {len(user_ids)} utilisateurs de test trouvés en BDD (les administrateurs sont préservés).")

    # 3. Identifier les profils candidats
    candidate_profile_ids = []
    if candidate_user_ids:
        cur.execute("SELECT id FROM candidate_profiles WHERE user_id = ANY(%s);", (candidate_user_ids,))
        candidate_profile_ids = [row[0] for row in cur.fetchall()]

    # 4. Identifier les offres de test
    job_offer_ids = []
    if company_ids:
        cur.execute("SELECT id FROM job_offers WHERE company_id = ANY(%s);", (company_ids,))
        job_offer_ids = [row[0] for row in cur.fetchall()]

    # Nettoyage préventif des tables d'audit / review liées aux entités de test
    for audit_table in ["company_verification_reviews", "account_status_audit_log", "job_offer_audit_log"]:
        try:
            cur.execute("""
                SELECT column_name FROM information_schema.columns 
                WHERE table_name = %s;
            """, (audit_table,))
            cols = [r[0] for r in cur.fetchall()]
            if not cols:
                continue
            if "company_id" in cols and company_ids:
                cur.execute(f"DELETE FROM {audit_table} WHERE company_id = ANY(%s);", (company_ids,))
            if "job_offer_id" in cols and job_offer_ids:
                cur.execute(f"DELETE FROM {audit_table} WHERE job_offer_id = ANY(%s);", (job_offer_ids,))
            if "target_user_id" in cols and user_ids:
                cur.execute(f"DELETE FROM {audit_table} WHERE target_user_id = ANY(%s);", (user_ids,))
            if "actor_user_id" in cols and user_ids:
                cur.execute(f"DELETE FROM {audit_table} WHERE actor_user_id = ANY(%s);", (user_ids,))
            if "user_id" in cols and user_ids:
                cur.execute(f"DELETE FROM {audit_table} WHERE user_id = ANY(%s);", (user_ids,))
        except Exception:
            pass

    # 5. Supprimer les candidatures liées
    if candidate_profile_ids or job_offer_ids:
        # Délier hired_application_id si présent pour éviter tout blocage de clé étrangère
        try:
            if job_offer_ids:
                cur.execute("UPDATE job_offers SET hired_application_id = NULL WHERE id = ANY(%s);", (job_offer_ids,))
        except Exception:
            pass

        cur.execute("""
            DELETE FROM applications 
            WHERE candidate_id = ANY(%s) OR job_offer_id = ANY(%s);
        """, (candidate_profile_ids or [0], job_offer_ids or [0]))
        deleted_apps = cur.rowcount
        print(f"   ✓ {deleted_apps} candidatures supprimées (avec entretiens et conversations en cascade).")

    # 6. Supprimer les offres d'emploi de test
    if company_ids:
        cur.execute("DELETE FROM job_offer_skills WHERE job_offer_id = ANY(%s);", (job_offer_ids or [0],))
        cur.execute("DELETE FROM job_offers WHERE company_id = ANY(%s);", (company_ids,))
        print(f"   ✓ {len(job_offer_ids)} offres d'emploi supprimées.")

    # 7. Supprimer les profils recruteurs et les entreprises
    if company_ids:
        cur.execute("DELETE FROM recruiter_profiles WHERE company_id = ANY(%s);", (company_ids,))
        cur.execute("DELETE FROM companies WHERE id = ANY(%s);", (company_ids,))
        print(f"   ✓ {len(company_ids)} entreprises et leurs profils recruteurs supprimés.")

    # 8. Supprimer les comptes utilisateurs (supprime candidate_profiles en cascade)
    if user_ids:
        cur.execute("DELETE FROM users WHERE id = ANY(%s) AND role != 'ADMIN' AND email != 'admin@joyn.test';", (user_ids,))
        print(f"   ✓ {cur.rowcount} comptes utilisateurs supprimés (candidats et recruteurs).")

    # Récupérer les IDs encore actifs en base (pour détecter et purger d'éventuels orphelins de médias)
    cur.execute("SELECT id FROM companies;")
    active_company_ids = set(row[0] for row in cur.fetchall())

    cur.execute("SELECT id FROM users;")
    active_user_ids = set(row[0] for row in cur.fetchall())

    cur.execute("SELECT id FROM candidate_profiles;")
    active_candidate_profile_ids = set(row[0] for row in cur.fetchall())

    conn.commit()
    cur.close()
    conn.close()

    # 9. Nettoyer les fichiers médias physiques
    print("\nNettoyage des fichiers médias...")

    # Nettoyage local
    if args.storage in ("local", "both"):
        local_company_targets = set(company_ids)
        local_user_targets = set(user_ids)
        local_resume_targets = set(candidate_profile_ids)

        if COMPANY_LOGOS_DIR.is_dir():
            for p in COMPANY_LOGOS_DIR.iterdir():
                if p.is_dir() and p.name.isdigit() and int(p.name) not in active_company_ids:
                    local_company_targets.add(int(p.name))
        if PROFILE_PICTURES_DIR.is_dir():
            for p in PROFILE_PICTURES_DIR.iterdir():
                if p.is_dir() and p.name.isdigit() and int(p.name) not in active_user_ids:
                    local_user_targets.add(int(p.name))
        if RESUME_DIR.is_dir():
            for p in RESUME_DIR.iterdir():
                if p.is_dir() and p.name.isdigit() and int(p.name) not in active_candidate_profile_ids:
                    local_resume_targets.add(int(p.name))

        for cid in sorted(local_company_targets):
            cleaner.delete_local_folder(COMPANY_LOGOS_DIR / str(cid))
        for uid in sorted(local_user_targets):
            cleaner.delete_local_folder(PROFILE_PICTURES_DIR / str(uid))
        for cand_id in sorted(local_resume_targets):
            cleaner.delete_local_folder(RESUME_DIR / str(cand_id))
        print("   ✓ Dossiers locaux media/ nettoyés.")

    # Nettoyage Supabase S3
    if args.storage in ("supabase", "both") and cleaner.s3_client:
        s3_company_targets = set(company_ids)
        s3_user_targets = set(user_ids)
        s3_resume_targets = set(candidate_profile_ids)

        # Détecter également les dossiers orphelins sur S3 (non présents dans les entités actives en base)
        s3_company_targets.update(cleaner.get_s3_subfolder_ids("company_logos/") - active_company_ids)
        s3_user_targets.update(cleaner.get_s3_subfolder_ids("profile_pictures/") - active_user_ids)
        s3_resume_targets.update(cleaner.get_s3_subfolder_ids("resumes/") - active_candidate_profile_ids)

        total_s3_deleted = 0
        for cid in sorted(s3_company_targets):
            total_s3_deleted += cleaner.delete_s3_prefix(f"company_logos/{cid}/")
        for uid in sorted(s3_user_targets):
            total_s3_deleted += cleaner.delete_s3_prefix(f"profile_pictures/{uid}/")
        for cand_id in sorted(s3_resume_targets):
            total_s3_deleted += cleaner.delete_s3_prefix(f"resumes/{cand_id}/")

        print(f"   ✓ {total_s3_deleted} fichiers nettoyés dans le bucket Supabase S3.")

    print("\n" + "="*75)
    print(" ROLLBACK TERMINÉ AVEC SUCCÈS : Base et médias remis au propre !")
    print("="*75)

if __name__ == "__main__":
    main()
