#!/usr/bin/env python3
"""
Script de peuplement de la base de données Joyn avec des données réalistes :
- Chargement dynamique depuis data/skills.json, data/companies_jobs.json et data/candidates.json
- Détection dynamique des colonnes (compatible V1 à V13 Flyway)
- Choix du stockage des médias : LOCAL (media/) ou SUPABASE (S3 joyn_file_bucket) via l'option --storage
- Logos d'entreprise designs (formes géométriques modernes, cubes 3D, anneaux, emblèmes)
- Photos de profil variées : avatars illustrés pour certains, initiales stylisées pour d'autres
- Arborescence propre et conforme à Joyn dans media/ (sans dossier en double)
- Candidatures réalistes (ACCEPTED, INTERVIEW, REJECTED, PENDING, VIEWED)
- 0 appel externe / 0 token Gemini consommé
"""

import os
import sys
import json
import uuid
import glob
import shutil
import hashlib
import random
import argparse
from pathlib import Path
from datetime import datetime, timedelta
from urllib.parse import urlparse
import io
import bcrypt
import psycopg2
from faker import Faker
from fpdf import FPDF
from PIL import Image, ImageDraw, ImageFont

fake = Faker('fr_FR')
Faker.seed(42)
random.seed(42)

# Chemins
BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
SKILLS_FILE = DATA_DIR / "skills.json"
COMPANIES_FILE = DATA_DIR / "companies_jobs.json"
CANDIDATES_FILE = DATA_DIR / "candidates.json"

MEDIA_DIR = BASE_DIR / "media"
RESUME_DIR = MEDIA_DIR / "candidate-resumes" / "resumes"
COMPANY_LOGOS_DIR = MEDIA_DIR / "company-logos" / "company_logos"
PROFILE_PICTURES_DIR = MEDIA_DIR / "profile-pictures" / "profile_pictures"

DEFAULT_PASSWORD = "Password123!"

def load_dotenv_file():
    """Charge automatiquement les variables d'environnement depuis le fichier .env si présent."""
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

# Chargement du fichier .env
load_dotenv_file()

def parse_db_url(url: str, user: str = None, password: str = None) -> dict:
    """Parse une URL de base de données PostgreSQL ou JDBC (ex: jdbc:postgresql://localhost:5432/joyn_db)."""
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
    """Construit la configuration de connexion PostgreSQL selon les variables .env_example."""
    db_url = os.getenv("DB_URL")
    db_user = os.getenv("DB_USER")
    db_password = os.getenv("DB_PASSWORD")

    if db_url:
        params = parse_db_url(db_url, user=db_user, password=db_password)
        # Valeurs par défaut si non spécifiées
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

# Palette de couleurs professionnelles
COLOR_PALETTES = [
    ((37, 99, 235), (96, 165, 250)),   # Bleu électrique
    ((16, 185, 129), (52, 211, 153)),  # Émeraude / Vert tech
    ((124, 58, 237), (167, 139, 250)), # Violet profond
    ((234, 88, 12), (251, 146, 60)),   # Ambre / Orange vif
    ((225, 29, 72), (251, 113, 133)),  # Rose / Corail
    ((13, 148, 136), (45, 212, 191)),  # Sarcelle / Cyan
    ((79, 70, 229), (129, 140, 248)),  # Indigo
    ((202, 138, 4), (250, 204, 21)),   # Jaune doré
    ((2, 132, 199), (56, 189, 248)),   # Bleu océan
    ((147, 51, 234), (192, 132, 252)), # Pourpre
]

# Recherche d'une police système TrueType
FONT_FILES = glob.glob("/usr/share/fonts/**/*.ttf", recursive=True)
SYSTEM_FONT_PATH = FONT_FILES[0] if FONT_FILES else None

class MediaStorageManager:
    """Gère le stockage des fichiers (local, supabase ou both)."""

    def __init__(self, mode: str = "local"):
        self.mode = mode.lower()
        self.s3_client = None
        self.bucket = os.getenv("SUPABASE_S3_BUCKET") or "joyn_file_bucket"

        if self.mode in ("supabase", "both"):
            self._init_supabase_s3()

    def _init_supabase_s3(self):
        try:
            import boto3
            from botocore.config import Config
        except ImportError:
            print("[ERREUR] Pour utiliser Supabase Storage, installez boto3 : pip install boto3")
            sys.exit(1)

        endpoint = os.getenv("SUPABASE_S3_ENDPOINT")
        access_key = os.getenv("SUPABASE_S3_ACCESS_KEY")
        secret_key = os.getenv("SUPABASE_S3_SECRET_KEY")
        region = os.getenv("SUPABASE_S3_REGION") or "eu-west-3"
        bucket = os.getenv("SUPABASE_S3_BUCKET") or "joyn_file_bucket"
        self.bucket = bucket

        if not endpoint or not access_key or not secret_key:
            print("[ERREUR] Variables d'environnement Supabase requises (cf. .env_example) :")
            print("  - SUPABASE_S3_ENDPOINT")
            print("  - SUPABASE_S3_ACCESS_KEY")
            print("  - SUPABASE_S3_SECRET_KEY")
            print("  - SUPABASE_S3_REGION (défaut: eu-west-3)")
            print("  - SUPABASE_S3_BUCKET (défaut: joyn_file_bucket)")
            sys.exit(1)

        print(f"Connexion au stockage Supabase S3 (bucket: {self.bucket}, region: {region})...")
        self.s3_client = boto3.client(
            's3',
            endpoint_url=endpoint,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=region,
            config=Config(s3={'addressing_style': 'path'})
        )

    def store(self, key: str, data: bytes, content_type: str, local_path: Path):
        """Enregistre le média en local et/ou sur le serveur Supabase."""
        # 1. Local
        if self.mode in ("local", "both"):
            local_path.parent.mkdir(parents=True, exist_ok=True)
            local_path.write_bytes(data)

        # 2. Supabase S3
        if self.mode in ("supabase", "both"):
            self.s3_client.put_object(
                Bucket=self.bucket,
                Key=key,
                Body=data,
                ContentType=content_type
            )

def load_json(path: Path):
    if not path.is_file():
        raise FileNotFoundError(
            f"Fichier de données introuvable : {path}. "
            "Assurez-vous que le dossier data/ contient bien vos fichiers JSON."
        )
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)

def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def get_font(size: int):
    try:
        return ImageFont.truetype(SYSTEM_FONT_PATH, size=size) if SYSTEM_FONT_PATH else ImageFont.load_default()
    except Exception:
        return ImageFont.load_default()

def get_table_columns(cur, table_name: str) -> set:
    cur.execute("""
        SELECT column_name 
        FROM information_schema.columns 
        WHERE table_name = %s;
    """, (table_name,))
    return {row[0].lower() for row in cur.fetchall()}

def insert_dynamic(cur, table_name: str, data: dict, table_cols: set, return_col: str = "id"):
    valid_items = {k: v for k, v in data.items() if k.lower() in table_cols}
    cols = list(valid_items.keys())
    values = [valid_items[k] for k in cols]
    placeholders = ["%s"] * len(cols)

    query = f"""
        INSERT INTO {table_name} ({', '.join(cols)})
        VALUES ({', '.join(placeholders)})
        RETURNING {return_col};
    """
    cur.execute(query, values)
    return cur.fetchone()[0]

def clean_redundant_directories():
    """Supprime les dossiers de doublons générés par erreur à la racine de company-logos et profile-pictures."""
    for parent in [MEDIA_DIR / "company-logos", MEDIA_DIR / "profile-pictures"]:
        if parent.is_dir():
            for item in parent.iterdir():
                if item.is_dir() and item.name.isdigit():
                    try:
                        shutil.rmtree(item)
                    except Exception:
                        pass

def draw_geometric_logo(company_name: str, size: int = 256) -> bytes:
    """Génère un logo design moderne avec formes géométriques stylisées."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Carte de fond blanche avec coins arrondis et bordure douce
    draw.rounded_rectangle([(8, 8), (size - 8, size - 8)], radius=48, fill=(255, 255, 255), outline=(226, 232, 240), width=4)

    cx, cy = size / 2, size / 2 - 14
    h = int(hashlib.md5(company_name.encode("utf-8")).hexdigest(), 16)
    c_dark, c_light = COLOR_PALETTES[h % len(COLOR_PALETTES)]
    motif = h % 5

    if motif == 0:
        # Cube isométrique / Hexagone 3D
        r = 50
        pts_top = [(cx, cy - r), (cx + r * 0.866, cy - r * 0.5), (cx, cy), (cx - r * 0.866, cy - r * 0.5)]
        pts_right = [(cx, cy), (cx + r * 0.866, cy - r * 0.5), (cx + r * 0.866, cy + r * 0.5), (cx, cy + r)]
        pts_left = [(cx, cy), (cx, cy + r), (cx - r * 0.866, cy + r * 0.5), (cx - r * 0.866, cy - r * 0.5)]
        draw.polygon(pts_top, fill=c_light)
        draw.polygon(pts_right, fill=c_dark)
        draw.polygon(pts_left, fill=(c_dark[0]//2 + c_light[0]//2, c_dark[1]//2 + c_light[1]//2, c_dark[2]//2 + c_light[2]//2))

    elif motif == 1:
        # Anneaux / Cercles imbriqués modernes
        draw.ellipse([(cx - 54, cy - 36), (cx + 14, cy + 32)], fill=c_dark)
        draw.ellipse([(cx - 14, cy - 36), (cx + 54, cy + 32)], fill=c_light)
        draw.chord([(cx - 14, cy - 36), (cx + 14, cy + 32)], start=270, end=90, fill=(255, 255, 255, 130))

    elif motif == 2:
        # Croix géométrique moderne arrondie (santé / services / qualité)
        arm = 52
        w = 18
        draw.rounded_rectangle([(cx - arm, cy - w), (cx + arm, cy + w)], radius=10, fill=c_dark)
        draw.rounded_rectangle([(cx - w, cy - arm), (cx + w, cy + arm)], radius=10, fill=c_light)

    elif motif == 3:
        # Pétales / Écologie / Géométrie organique
        draw.chord([(cx - 48, cy - 48), (cx + 48, cy + 48)], start=0, end=180, fill=c_dark)
        draw.chord([(cx - 38, cy - 38), (cx + 38, cy + 38)], start=180, end=360, fill=c_light)

    else:
        # Losange tech origami
        draw.polygon([(cx, cy - 54), (cx + 48, cy), (cx, cy + 44), (cx - 48, cy)], fill=c_dark)
        draw.polygon([(cx, cy - 30), (cx + 28, cy), (cx, cy + 24), (cx - 28, cy)], fill=(255, 255, 255))
        draw.ellipse([(cx - 13, cy - 13), (cx + 13, cy + 13)], fill=c_light)

    font_init = get_font(24)
    display_name = company_name[:14]
    bbox = draw.textbbox((0, 0), display_name, font=font_init)
    tw = bbox[2] - bbox[0]
    draw.text(((size - tw) / 2, size - 44), display_name, fill=(71, 85, 105), font=font_init)

    buf = io.BytesIO()
    img.save(buf, format="WEBP", quality=92)
    return buf.getvalue()

def draw_profile_picture(user_id: int, full_name: str, size: int = 256) -> bytes:
    """Génère un avatar illustré flat-design unique pour chaque utilisateur (aucunes initiales)."""
    h = int(hashlib.md5(f"{user_id}_{full_name}".encode("utf-8")).hexdigest(), 16)

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx, cy = size / 2, size / 2

    bg_colors = [
        (219, 234, 254), # Bleu pastel
        (254, 226, 226), # Rose doux
        (254, 243, 199), # Jaune clair
        (209, 250, 229), # Vert menthe
        (243, 232, 255), # Lavande
        (224, 242, 254), # Ciel
        (254, 215, 170), # Pêche
        (241, 245, 249), # Gris perle
        (204, 251, 241), # Turquoise doux
        (252, 231, 243), # Fushia pastel
    ]
    skin_tones = [
        (255, 224, 189),
        (241, 194, 125),
        (224, 172, 105),
        (141, 85, 36),
        (198, 134, 66),
        (255, 205, 148),
        (255, 229, 200),
        (115, 64, 25)
    ]
    hair_colors = [
        (30, 41, 59),    # Noir ébène
        (67, 40, 24),    # Brun chocolat
        (120, 53, 15),   # Châtain chaud
        (180, 83, 9),    # Roux / Cuivré
        (202, 138, 4),   # Blond doré
        (100, 116, 139), # Gris argent
        (15, 23, 42)     # Noir de jais
    ]
    shirt_colors = [
        (59, 130, 246), (16, 185, 129), (239, 68, 68), (139, 92, 246),
        (14, 165, 233), (249, 115, 22), (236, 72, 153), (16, 149, 193),
        (79, 70, 229), (20, 184, 166)
    ]

    bg = bg_colors[h % len(bg_colors)]
    skin = skin_tones[(h >> 2) % len(skin_tones)]
    hair = hair_colors[(h >> 4) % len(hair_colors)]
    shirt = shirt_colors[(h >> 6) % len(shirt_colors)]

    # 1. Fond circulaire doux
    draw.ellipse([(6, 6), (size - 6, size - 6)], fill=bg)

    # 2. Buste et encolure
    draw.chord([(cx - 82, cy + 34), (cx + 82, cy + 188)], start=0, end=360, fill=shirt)
    collar_type = (h >> 3) % 2
    if collar_type == 0:
        draw.chord([(cx - 24, cy + 30), (cx + 24, cy + 62)], start=0, end=180, fill=skin)
    else:
        draw.polygon([(cx - 20, cy + 32), (cx + 20, cy + 32), (cx, cy + 60)], fill=skin)

    # 3. Cou
    draw.rectangle([(cx - 17, cy + 18), (cx + 17, cy + 42)], fill=skin)

    # 4. Tête / Visage
    draw.ellipse([(cx - 42, cy - 50), (cx + 42, cy + 30)], fill=skin)

    # 5. Oreilles
    draw.ellipse([(cx - 46, cy - 14), (cx - 38, cy + 6)], fill=skin)
    draw.ellipse([(cx + 38, cy - 14), (cx + 46, cy + 6)], fill=skin)

    # 6. Cheveux (6 styles variés)
    style = (h >> 1) % 6
    if style == 0:
        # Court moderne avec mèche
        draw.chord([(cx - 44, cy - 56), (cx + 44, cy - 4)], start=180, end=360, fill=hair)
        draw.rectangle([(cx - 44, cy - 35), (cx - 36, cy - 10)], fill=hair)
    elif style == 1:
        # Long ondulé
        draw.chord([(cx - 46, cy - 56), (cx + 46, cy + 10)], start=180, end=360, fill=hair)
        draw.rectangle([(cx - 46, cy - 25), (cx - 36, cy + 48)], fill=hair)
        draw.rectangle([(cx + 36, cy - 25), (cx + 46, cy + 48)], fill=hair)
    elif style == 2:
        # Chignon haut élégant
        draw.ellipse([(cx - 25, cy - 74), (cx + 25, cy - 42)], fill=hair)
        draw.chord([(cx - 44, cy - 54), (cx + 44, cy - 8)], start=180, end=360, fill=hair)
    elif style == 3:
        # Afro / Bouclé volumineux
        draw.ellipse([(cx - 50, cy - 62), (cx + 50, cy + 12)], fill=hair)
        draw.ellipse([(cx - 40, cy - 44), (cx + 40, cy + 30)], fill=skin)
    elif style == 4:
        # Coupe mi-longue / Carré géométrique
        draw.chord([(cx - 46, cy - 56), (cx + 46, cy - 2)], start=180, end=360, fill=hair)
        draw.rounded_rectangle([(cx - 46, cy - 20), (cx - 36, cy + 32)], radius=5, fill=hair)
        draw.rounded_rectangle([(cx + 36, cy - 20), (cx + 46, cy + 32)], radius=5, fill=hair)
    else:
        # Coupe courte / Dégradé avec barbe stylisée
        draw.chord([(cx - 43, cy - 54), (cx + 43, cy - 20)], start=180, end=360, fill=hair)
        draw.chord([(cx - 36, cy - 5), (cx + 36, cy + 31)], start=0, end=180, fill=hair)

    # 7. Yeux
    eye_color = (30, 41, 59)
    draw.ellipse([(cx - 18, cy - 10), (cx - 12, cy - 4)], fill=eye_color)
    draw.ellipse([(cx + 12, cy - 10), (cx + 18, cy - 4)], fill=eye_color)

    # 8. Lunettes pour certains profils (~35%)
    has_glasses = ((h >> 5) % 3 == 0)
    if has_glasses:
        frame_color = (51, 65, 85)
        draw.rounded_rectangle([(cx - 24, cy - 14), (cx - 6, cy + 2)], radius=4, outline=frame_color, width=2)
        draw.rounded_rectangle([(cx + 6, cy - 14), (cx + 24, cy + 2)], radius=4, outline=frame_color, width=2)
        draw.line([(cx - 6, cy - 6), (cx + 6, cy - 6)], fill=frame_color, width=2)

    # 9. Sourire
    draw.arc([(cx - 14, cy + 2), (cx + 14, cy + 18)], start=20, end=160, fill=(185, 28, 28), width=3)

    # 10. Masque circulaire pour découper parfaitement le tour
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([(6, 6), (size - 6, size - 6)], fill=255)
    img.putalpha(mask)

    buf = io.BytesIO()
    img.save(buf, format="WEBP", quality=90)
    return buf.getvalue()

def save_company_logo(company_id: int, company_name: str, storage: MediaStorageManager) -> str:
    """Génère le logo et le stocke (local et/ou supabase)."""
    uuid_str = str(uuid.uuid4())
    key = f"company_logos/{company_id}/{uuid_str}.webp"
    data = draw_geometric_logo(company_name)
    local_path = COMPANY_LOGOS_DIR / str(company_id) / f"{uuid_str}.webp"
    storage.store(key, data, "image/webp", local_path)
    return key

def save_profile_picture(user_id: int, full_name: str, storage: MediaStorageManager) -> str:
    """Génère la photo de profil et la stocke (local et/ou supabase)."""
    uuid_str = str(uuid.uuid4())
    key = f"profile_pictures/{user_id}/{uuid_str}.webp"
    data = draw_profile_picture(user_id, full_name)
    local_path = PROFILE_PICTURES_DIR / str(user_id) / f"{uuid_str}.webp"
    storage.store(key, data, "image/webp", local_path)
    return key

def create_resume_pdf(candidate_id: int, cand_info: dict, experiences: list, educations: list, skills: list, storage: MediaStorageManager) -> str:
    """Génère un CV PDF et le stocke (local et/ou supabase)."""
    uuid_str = str(uuid.uuid4())
    key = f"resumes/{candidate_id}/{uuid_str}.pdf"

    pdf = FPDF()
    pdf.add_page()
    pdf.set_auto_page_break(auto=True, margin=15)
    
    # En-tête
    pdf.set_font("Helvetica", 'B', 18)
    pdf.cell(0, 9, f"{cand_info['first_name']} {cand_info['last_name']}", ln=True)
    pdf.set_font("Helvetica", 'B', 12)
    pdf.set_text_color(50, 50, 150)
    pdf.cell(0, 8, cand_info['headline'], ln=True)
    pdf.set_text_color(0, 0, 0)
    pdf.set_font("Helvetica", size=9)
    pdf.cell(0, 5, f"Email: {cand_info['email']} | Tel: {cand_info['phone']} | Ville: {cand_info['location']}", ln=True)
    pdf.ln(4)

    # Profil
    pdf.set_font("Helvetica", 'B', 11)
    pdf.cell(0, 7, "PROFIL", ln=True)
    pdf.set_font("Helvetica", size=9)
    pdf.multi_cell(0, 5, cand_info['bio'])
    pdf.ln(3)

    # Expériences
    pdf.set_font("Helvetica", 'B', 11)
    pdf.cell(0, 7, "EXPERIENCES PROFESSIONNELLES", ln=True)
    pdf.set_font("Helvetica", size=9)
    for exp in experiences:
        pdf.set_font("Helvetica", 'B', 9)
        pdf.cell(0, 5, f"{exp['title']} - {exp['organization']} ({exp['start']} a {exp['end']})", ln=True)
        pdf.set_font("Helvetica", size=9)
        pdf.multi_cell(0, 5, exp['description'])
        pdf.ln(1)
    pdf.ln(2)

    # Formations
    pdf.set_font("Helvetica", 'B', 11)
    pdf.cell(0, 7, "FORMATION & DIPLOMES", ln=True)
    for edu in educations:
        pdf.set_font("Helvetica", 'B', 9)
        pdf.cell(0, 5, f"{edu['degree']} en {edu['field']} - {edu['school']} ({edu['start_year']}-{edu['end_year']})", ln=True)
    pdf.ln(4)

    # Compétences
    pdf.set_font("Helvetica", 'B', 11)
    pdf.cell(0, 7, "COMPETENCES TECHNIQUES", ln=True)
    pdf.set_font("Helvetica", size=9)
    skill_names = [s[0] if isinstance(s, (list, tuple)) else str(s) for s in skills]
    pdf.cell(0, 5, ", ".join(skill_names), ln=True)

    pdf_bytes = bytes(pdf.output())
    local_path = RESUME_DIR / str(candidate_id) / f"{uuid_str}.pdf"
    storage.store(key, pdf_bytes, "application/pdf", local_path)
    return key

def parse_arguments():
    parser = argparse.ArgumentParser(description="Seed de la base de données Joyn avec données de test réalistes.")
    default_storage = os.getenv("JOYN_STORAGE_PROVIDER", os.getenv("STORAGE_PROVIDER", "local")).lower()
    if default_storage not in ("local", "supabase", "both"):
        default_storage = "local"
    parser.add_argument(
        "--storage", "-s",
        choices=["local", "supabase", "both"],
        default=default_storage,
        help="Cible de stockage des médias : 'local' (media/), 'supabase' (S3 cloud) ou 'both' (les deux). Défaut: local."
    )
    return parser.parse_args()

def main():
    args = parse_arguments()
    storage_manager = MediaStorageManager(mode=args.storage)

    print("="*75)
    print(" PEUPLEMENT DE LA BASE DE DONNÉES JOYN")
    print("="*75)
    print(f"-> Mode de stockage des médias : {args.storage.upper()}")

    skills_data = load_json(SKILLS_FILE)
    companies_data = load_json(COMPANIES_FILE)
    candidates_data = load_json(CANDIDATES_FILE)

    print(f"-> {len(skills_data)} compétences dans {SKILLS_FILE.name}")
    print(f"-> {len(companies_data)} entreprises dans {COMPANIES_FILE.name}")
    print(f"-> {len(candidates_data)} profils candidats dans {CANDIDATES_FILE.name}")

    if args.storage in ("local", "both"):
        clean_redundant_directories()

    db_params = get_db_connection_params()
    host_display = f"{db_params.get('host', 'localhost')}:{db_params.get('port', 5432)}/{db_params.get('dbname', 'joyn_db')}"
    print(f"\nConnexion à PostgreSQL ({host_display}, user: {db_params.get('user', 'joyn_user')})...")
    try:
        conn = psycopg2.connect(**db_params)
        conn.autocommit = False
        cur = conn.cursor()
    except Exception as e:
        print(f"[ERREUR] Impossible de se connecter à PostgreSQL : {e}")
        print("Vérifiez les variables DB_URL, DB_USER, DB_PASSWORD dans votre fichier .env ou variables d'environnement.")
        sys.exit(1)

    company_cols = get_table_columns(cur, "companies")
    job_cols = get_table_columns(cur, "job_offers")
    user_cols = get_table_columns(cur, "users")
    candidate_cols = get_table_columns(cur, "candidate_profiles")

    print("Calcul du hash BCrypt pour les comptes de test...")
    pwd_hash = hash_password(DEFAULT_PASSWORD)

    # 1. Insertion des Compétences
    print("\nInsertion / synchronisation des compétences...")
    skill_ids = {}
    for item in skills_data:
        name = item["name"] if isinstance(item, dict) else str(item)
        cur.execute("""
            INSERT INTO skills (name) VALUES (%s)
            ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
            RETURNING id;
        """, (name,))
        skill_ids[name] = cur.fetchone()[0]

    cur.execute("SELECT code, id FROM contract_types;")
    contract_types = {code: cid for code, cid in cur.fetchall()}
    if not contract_types:
        print("[ERREUR] Aucun type de contrat trouvé. Exécutez les migrations Flyway.")
        sys.exit(1)

    cur.execute("SELECT code, id FROM application_statuses;")
    app_statuses = {code: sid for code, sid in cur.fetchall()}

    # 2. Insertion des Entreprises, Logos et Offres
    print(f"\nInsertion des entreprises, génération des logos ({args.storage}) et des offres...")
    company_jobs_map = {}

    for c in companies_data:
        if "deleted_at" in company_cols:
            cur.execute("SELECT id FROM companies WHERE name = %s AND deleted_at IS NULL;", (c['name'],))
        else:
            cur.execute("SELECT id FROM companies WHERE name = %s;", (c['name'],))
        row = cur.fetchone()

        siret = c.get('siret') or fake.numerify("##############")
        website = c.get('website') or f"https://www.{c.get('domain', 'example.com')}"

        if row:
            company_id = row[0]
        else:
            comp_insert_data = {
                "name": c['name'],
                "description": c.get('desc'),
                "website": website,
                "siret": siret,
                "sector": c.get('sector'),
                "company_size": c.get('size', 'SMALL'),
                "address": c.get('location', 'France'),
                "verification_status": 'VERIFIED',
                "status": 'ACTIVE',
                "check_siret_found": False
            }
            company_id = insert_dynamic(cur, "companies", comp_insert_data, company_cols)

        # Génération du logo géométrique WebP et stockage
        logo_key = save_company_logo(company_id, c['name'], storage_manager)
        cur.execute("""
            UPDATE companies
            SET description = %s, website = %s, logo_url = %s, sector = %s, company_size = %s, address = %s
            WHERE id = %s;
        """, (c.get('desc'), website, logo_key, c.get('sector'), c.get('size', 'SMALL'), c.get('location', 'France'), company_id))

        # Recruteur
        r_email, r_first, r_last = c['recruiter']
        cur.execute("SELECT id FROM users WHERE LOWER(email) = LOWER(%s);", (r_email,))
        row = cur.fetchone()
        if row:
            r_user_id = row[0]
            cur.execute("SELECT id FROM recruiter_profiles WHERE user_id = %s;", (r_user_id,))
            recruiter_id = cur.fetchone()[0]
        else:
            user_insert_data = {
                "email": r_email,
                "password_hash": pwd_hash,
                "role": 'RECRUITER',
                "status": 'ACTIVE',
                "email_verified_at": datetime.now()
            }
            r_user_id = insert_dynamic(cur, "users", user_insert_data, user_cols)

            cur.execute("""
                INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name)
                VALUES (%s, %s, %s, %s)
                RETURNING id;
            """, (r_user_id, company_id, r_first, r_last))
            recruiter_id = cur.fetchone()[0]

        # Génération et stockage de la photo de profil du recruteur
        recruiter_pic_key = save_profile_picture(r_user_id, f"{r_first} {r_last}", storage_manager)
        if "profile_picture_url" in user_cols:
            cur.execute("UPDATE users SET profile_picture_url = %s WHERE id = %s;", (recruiter_pic_key, r_user_id))

        # Offres d'emploi
        for job in c.get('jobs', []):
            raw_contract = job.get('contract', 'CDI')
            contract_code = 'APPRENTICESHIP' if raw_contract in ('ALTERNANCE', 'APPRENTICESHIP') else raw_contract
            contract_id = contract_types.get(contract_code, list(contract_types.values())[0])

            if "deleted_at" in job_cols:
                cur.execute("""
                    SELECT id FROM job_offers
                    WHERE company_id = %s AND title = %s AND deleted_at IS NULL;
                """, (company_id, job['title']))
            else:
                cur.execute("""
                    SELECT id FROM job_offers
                    WHERE company_id = %s AND title = %s;
                """, (company_id, job['title']))
            job_row = cur.fetchone()

            if job_row:
                job_id = job_row[0]
            else:
                job_insert_data = {
                    "company_id": company_id,
                    "recruiter_id": recruiter_id,
                    "contract_type_id": contract_id,
                    "title": job['title'],
                    "description": job.get('description', f"Poste de {job['title']} chez {c['name']}."),
                    "location": job.get('location', c.get('location', 'France')),
                    "salary_min": job.get('salary_min'),
                    "salary_max": job.get('salary_max'),
                    "salary_currency": 'EUR',
                    "salary_period": 'YEAR',
                    "remote_mode": job.get('remote', 'HYBRID'),
                    "status": 'PUBLISHED',
                    "degree_level_required": job.get('degree', 'BAC_3'),
                    "experience_level_required": job.get('exp', 'MID'),
                    "published_at": datetime.now()
                }
                job_id = insert_dynamic(cur, "job_offers", job_insert_data, job_cols)

            company_jobs_map[(c['name'], job['title'])] = {
                "job_id": job_id,
                "recruiter_user_id": r_user_id
            }

            for s_name in job.get('skills', []):
                if s_name not in skill_ids:
                    raise ValueError(f"Compétence '{s_name}' inconnue pour l'offre '{job['title']}'")
                cur.execute("""
                    INSERT INTO job_offer_skills (job_offer_id, skill_id, required_level)
                    VALUES (%s, %s, 'INTERMEDIATE')
                    ON CONFLICT DO NOTHING;
                """, (job_id, skill_ids[s_name]))

    # 3. Insertion des Candidats, Photos de profil et CVs PDF
    print(f"\nInsertion des candidats, photos de profil ({args.storage}) et CVs PDF...")
    candidate_map = {}

    for cdata in candidates_data:
        cur.execute("SELECT id FROM users WHERE LOWER(email) = LOWER(%s);", (cdata['email'],))
        row = cur.fetchone()
        if row:
            cand_user_id = row[0]
            cur.execute("SELECT id FROM candidate_profiles WHERE user_id = %s;", (cand_user_id,))
            cand_id = cur.fetchone()[0]
        else:
            cand_user_data = {
                "email": cdata['email'],
                "password_hash": pwd_hash,
                "role": 'CANDIDATE',
                "status": 'ACTIVE',
                "email_verified_at": datetime.now()
            }
            cand_user_id = insert_dynamic(cur, "users", cand_user_data, user_cols)

            cand_profile_data = {
                "user_id": cand_user_id,
                "first_name": cdata['first_name'],
                "last_name": cdata['last_name'],
                "headline": cdata['headline'],
                "bio": cdata['bio'],
                "location": cdata['location'],
                "phone": cdata['phone'],
                "years_of_experience": cdata['exp_years'],
                "pref_remote_policy": 'HYBRID',
                "pref_min_salary": 35000
            }
            cand_id = insert_dynamic(cur, "candidate_profiles", cand_profile_data, candidate_cols)

            # Expériences
            for exp in cdata.get('experiences', []):
                cur.execute("""
                    INSERT INTO candidate_experiences (candidate_id, title, organization, description, start_date, end_date)
                    VALUES (%s, %s, %s, %s, %s, %s);
                """, (cand_id, exp['title'], exp['organization'], exp['description'], exp['start'], exp['end']))

            # Formations
            for edu in cdata.get('educations', []):
                cur.execute("""
                    INSERT INTO candidate_educations (candidate_id, school_name, degree, field_of_study, start_year, end_year)
                    VALUES (%s, %s, %s, %s, %s, %s);
                """, (cand_id, edu['school'], edu['degree'], edu['field'], edu['start_year'], edu['end_year']))

            # Compétences
            for skill_entry in cdata.get('skills', []):
                s_name, lvl = skill_entry if isinstance(skill_entry, (list, tuple)) else (skill_entry, 'INTERMEDIATE')
                if s_name not in skill_ids:
                    raise ValueError(f"Compétence candidat '{s_name}' inconnue dans data/skills.json")
                cur.execute("""
                    INSERT INTO candidate_skills (candidate_id, skill_id, level)
                    VALUES (%s, %s, %s)
                    ON CONFLICT DO NOTHING;
                """, (cand_id, skill_ids[s_name], lvl))

        # Génération physique et stockage de la photo de profil
        cand_full_name = f"{cdata['first_name']} {cdata['last_name']}"
        cand_pic_key = save_profile_picture(cand_user_id, cand_full_name, storage_manager)

        if "profile_picture_url" in user_cols:
            cur.execute("UPDATE users SET profile_picture_url = %s WHERE id = %s;", (cand_pic_key, cand_user_id))
        if "photo_url" in candidate_cols:
            cur.execute("UPDATE candidate_profiles SET photo_url = %s WHERE id = %s;", (cand_pic_key, cand_id))

        # Génération et stockage du CV PDF
        cand_pdf_key = create_resume_pdf(
            cand_id, cdata, cdata.get('experiences', []),
            cdata.get('educations', []), cdata.get('skills', []),
            storage_manager
        )
        cur.execute("UPDATE candidate_profiles SET resume_url = %s WHERE id = %s;", (cand_pdf_key, cand_id))

        candidate_map[cdata['email']] = {
            "cand_id": cand_id,
            "user_id": cand_user_id,
            "name": cand_full_name
        }

    # 4. Création des Candidatures & Scénarios (ACCEPTED, INTERVIEW, REJECTED, PENDING, VIEWED)
    print("\nCréation des candidatures et scénarios d'interaction...")
    application_scenarios = [
        # Alexandre Martin -> Atout Numérique (ACCEPTED avec conversation)
        {
            "cand_email": "alexandre.martin@joyn.test",
            "company": "Atout Numérique",
            "job_title": "Développeur·euse backend Python",
            "status": "ACCEPTED",
            "message": "Bonjour, très intéressé par vos projets backend. Mon profil Python et PostgreSQL correspond parfaitement !",
            "viewed": True
        },
        # Alexandre Martin -> Nova Conseil Public (VIEWED)
        {
            "cand_email": "alexandre.martin@joyn.test",
            "company": "Nova Conseil Public",
            "job_title": "Consultant·e transformation publique",
            "status": "VIEWED",
            "message": "Bonjour, je souhaite apporter mon expérience d'architecture au service de la transformation publique.",
            "viewed": True
        },
        # Sophie Bernard -> Studio Pixel (INTERVIEW avec entretien planifié)
        {
            "cand_email": "sophie.bernard@joyn.test",
            "company": "Studio Pixel",
            "job_title": "Directeur·rice artistique junior",
            "status": "INTERVIEW",
            "message": "Bonjour ! Passionnée par le design graphique et la narration visuelle, j'aimerais rejoindre Studio Pixel.",
            "viewed": True
        },
        # Sophie Bernard -> Studio Pixel (REJECTED)
        {
            "cand_email": "sophie.bernard@joyn.test",
            "company": "Studio Pixel",
            "job_title": "Monteur·euse vidéo",
            "status": "REJECTED",
            "message": "Bonjour, je pose également ma candidature pour la partie montage.",
            "viewed": True
        },
        # Claire Petit -> Robotik Industrie (PENDING - Alternance)
        {
            "cand_email": "claire.petit@joyn.test",
            "company": "Robotik Industrie",
            "job_title": "Technicien·ne robotique",
            "status": "PENDING",
            "message": "Bonjour, actuellement en formation, je recherche une alternance stimulante en intégration robotique.",
            "viewed": False
        },
        # Julien Moreau -> AgriNova (VIEWED)
        {
            "cand_email": "julien.moreau@joyn.test",
            "company": "AgriNova",
            "job_title": "Conseiller·ère agronome",
            "status": "VIEWED",
            "message": "Bonjour, engagé pour l'agroécologie, j'aimerais accompagner vos agriculteurs partenaires.",
            "viewed": True
        },
        # Inès Garcia -> Clinique des Rives (ACCEPTED avec conversation)
        {
            "cand_email": "ines.garcia@joyn.test",
            "company": "Clinique des Rives",
            "job_title": "Infirmier·ère diplômé·e d'État",
            "status": "ACCEPTED",
            "message": "Bonjour, infirmière passionnée par le relationnel et le soin pluridisciplinaire, je candidate avec enthousiasme.",
            "viewed": True
        }
    ]

    for scen in application_scenarios:
        cand_data = candidate_map.get(scen['cand_email'])
        job_data = company_jobs_map.get((scen['company'], scen['job_title']))

        if not cand_data or not job_data:
            continue

        status_id = app_statuses.get(scen['status'], 1)
        cur.execute("""
            SELECT id FROM applications WHERE candidate_id = %s AND job_offer_id = %s;
        """, (cand_data['cand_id'], job_data['job_id']))
        app_row = cur.fetchone()

        if not app_row:
            cur.execute("""
                INSERT INTO applications (candidate_id, job_offer_id, status_id, viewed_at, cover_message, resume_url)
                VALUES (%s, %s, %s, %s, %s, %s)
                RETURNING id;
            """, (
                cand_data['cand_id'], job_data['job_id'], status_id,
                datetime.now() if scen['viewed'] else None,
                scen['message'], "resume_key"
            ))
            app_id = cur.fetchone()[0]

            if scen['status'] in ("INTERVIEW", "ACCEPTED"):
                cur.execute("""
                    INSERT INTO conversations (application_id, is_unlocked)
                    VALUES (%s, TRUE) RETURNING id;
                """, (app_id,))
                conv_id = cur.fetchone()[0]

                cur.execute("""
                    INSERT INTO messages (conversation_id, sender_user_id, content, read_at)
                    VALUES (%s, %s, %s, NOW());
                """, (conv_id, job_data['recruiter_user_id'], f"Bonjour {cand_data['name']}, votre profil correspond vivement à nos attentes !"))

                cur.execute("""
                    INSERT INTO messages (conversation_id, sender_user_id, content, read_at)
                    VALUES (%s, %s, %s, NOW());
                """, (conv_id, cand_data['user_id'], "Merci beaucoup ! Je me tiens à votre entière disposition."))

                if scen['status'] == "INTERVIEW":
                    cur.execute("""
                        INSERT INTO interviews (application_id, proposed_by, scheduled_at, duration_minutes, mode, location_or_link, status)
                        VALUES (%s, %s, %s, 45, 'VIDEO', 'https://meet.google.com/joyn-demo-interview', 'CONFIRMED');
                    """, (app_id, job_data['recruiter_user_id'], datetime.now() + timedelta(days=2)))

    conn.commit()
    cur.close()
    conn.close()

    print("\n" + "="*75)
    print(" PEUPLEMENT TERMINÉ AVEC SUCCÈS !")
    print("="*75)
    print(f"🔒 Mot de passe commun : {DEFAULT_PASSWORD}")
    print(f"📦 Stockage utilisé : {args.storage.upper()}")
    if args.storage in ("local", "both"):
        print("  - Fichiers locaux dans media/ (company-logos, profile-pictures, candidate-resumes)")
    if args.storage in ("supabase", "both"):
        print(f"  - Objets uploadés dans le bucket Supabase '{storage_manager.bucket}'")
    print("="*75)

if __name__ == "__main__":
    main()
