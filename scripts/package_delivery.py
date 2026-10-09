"""Package buildable source without local caches, SDK paths or captured documents.

Run with Python 3 from the project root after validation and APK copying.
Bundled publisher model weights are intentionally included in the source archive.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "deliverables"
OUT.mkdir(exist_ok=True)

root_files = [
    "README.md", "DESIGN.md", "UX-CONTRACT.md", "premium-ui.json", ".gitignore",
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    "app/build.gradle.kts", "app/proguard-rules.pro",
]
files = [ROOT / name for name in root_files]
for directory in ["app/src", "app/schemas", "gradle", "scripts", "docs"]:
    files.extend(path for path in (ROOT / directory).rglob("*") if path.is_file())
files = sorted(set(files))
archive = OUT / "GabayAI-source.zip"
with ZipFile(archive, "w", compression=ZIP_DEFLATED, compresslevel=6, allowZip64=True) as bundle:
    for path in files:
        if "__pycache__" in path.parts or path.suffix == ".pyc":
            continue
        # Already compressed neural weights need no second costly compression.
        compression = ZIP_STORED if path.suffix == ".onnx" else ZIP_DEFLATED
        bundle.write(path, "GabayAI/" + path.relative_to(ROOT).as_posix(), compress_type=compression)
with ZipFile(archive) as bundle:
    assert bundle.testzip() is None, "Source archive failed CRC verification"
    names = bundle.namelist()
    assert not any("/build/" in name or name.endswith("local.properties") for name in names)
    assert any(name.endswith("gradle-wrapper.jar") for name in names)
    models = {name for name in names if name.endswith(".onnx")}
    assert models == {
        "GabayAI/app/src/enhanced/assets/models/bge/model.onnx",
        "GabayAI/app/src/semantic/assets/models/minilm/model_qint8_arm64.onnx",
    }, "Source archive must contain exactly the two working flavor models"

def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

inventory = [
    {"file": path.name, "bytes": path.stat().st_size, "sha256": sha256(path)}
    for path in sorted(OUT.iterdir()) if path.suffix in {".apk", ".zip"}
]
(OUT / "SHA256SUMS.txt").write_text(
    "".join(f'{item["sha256"]}  {item["file"]}\n' for item in inventory), encoding="utf-8")
(OUT / "manifest.json").write_text(json.dumps(inventory, indent=2) + "\n", encoding="utf-8")
print(json.dumps(inventory, indent=2))
