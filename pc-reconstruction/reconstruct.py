#!/usr/bin/env python3
"""Ricostruzione sparsa da archivio fotografico CasaScan 3D con COLMAP."""
import argparse
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

def run(*args):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run([str(x) for x in args], check=True)

def main():
    p = argparse.ArgumentParser(description="CasaScan 3D - COLMAP")
    p.add_argument("input_zip", type=Path)
    p.add_argument("--output", type=Path, default=Path("casascan_output"))
    p.add_argument("--colmap", default="colmap")
    args = p.parse_args()
    if not args.input_zip.is_file(): p.error("ZIP non trovato")
    if not shutil.which(args.colmap) and not Path(args.colmap).is_file(): p.error("COLMAP non trovato")
    dest = args.output.resolve()
    dest.mkdir(parents=True, exist_ok=True)
    images = dest / "images"
    images.mkdir(exist_ok=True)
    with zipfile.ZipFile(args.input_zip) as z:
        for entry in z.infolist():
            if not entry.filename.startswith("images/") or not entry.filename.lower().endswith((".jpg", ".jpeg", ".png")):
                continue
            name = Path(entry.filename).name
            if not name: continue
            with z.open(entry) as source, (images / name).open("wb") as target:
                shutil.copyfileobj(source, target)
    if len(list(images.iterdir())) < 3: p.error("Servono almeno 3 fotografie")
    db = dest / "database.db"
    sparse = dest / "sparse"
    sparse.mkdir(exist_ok=True)
    try:
        run(args.colmap, "feature_extractor", "--database_path", db, "--image_path", images, "--ImageReader.single_camera", "1", "--SiftExtraction.use_gpu", "0")
        run(args.colmap, "exhaustive_matcher", "--database_path", db, "--SiftMatching.use_gpu", "0")
        run(args.colmap, "mapper", "--database_path", db, "--image_path", images, "--output_path", sparse)
        models = [d for d in sparse.iterdir() if d.is_dir() and (d / "cameras.bin").exists()]
        if not models: raise RuntimeError("Nessun modello creato. Acquisisci più fotografie sovrapposte.")
        model = max(models, key=lambda d: len(list(d.iterdir())))
        run(args.colmap, "model_converter", "--input_path", model, "--output_path", dest / "sparse.ply", "--output_type", "PLY")
        print("Nuvola di punti:", dest / "sparse.ply")
    except subprocess.CalledProcessError as exc:
        print("COLMAP ha fallito:", exc, file=sys.stderr)
        sys.exit(exc.returncode or 1)

if __name__ == "__main__":
    main()
