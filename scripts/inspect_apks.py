"""Inspect final optimized APK contents and independently verify model bytes."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SDK_TOOLS = Path(r"C:\Users\dever\AppData\Local\Android\Sdk\build-tools\36.1.0")
EXPECTED = {
    "semantic": ("assets/models/minilm/model_qint8_arm64.onnx", 23_026_053,
                 "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"),
    "enhanced": ("assets/models/bge/model.onnx", 1_336_749_542,
                 "deb82329ff0e9725a0d8407e434be40a7793725ce4e3032d8091f615130d0105"),
}
reports = []
for flavor in ("base", "semantic", "enhanced"):
    apk = ROOT / f"app/build/outputs/apk/{flavor}/release/app-{flavor}-release.apk"
    permissions = subprocess.check_output([str(SDK_TOOLS / "aapt2.exe"), "dump", "permissions", str(apk)], text=True)
    assert "android.permission.INTERNET" not in permissions
    assert "android.permission.ACCESS_NETWORK_STATE" not in permissions
    assert "android.permission.CAMERA" in permissions
    assert "versionCode='2'" in subprocess.check_output(
        [str(SDK_TOOLS / "aapt2.exe"), "dump", "badging", str(apk)], text=True)
    with ZipFile(apk) as archive:
        names = archive.namelist()
        models = [name for name in names if name.endswith(".onnx")]
        libraries = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        assert {name.split("/")[1] for name in libraries} == {"arm64-v8a"}
        if flavor == "base":
            assert not models and not any("onnxruntime" in name for name in libraries)
            model_info = None
        else:
            name, expected_bytes, expected_hash = EXPECTED[flavor]
            assert models == [name], f"Unexpected extra/failed model in {flavor}: {models}"
            entry = archive.getinfo(name)
            assert entry.file_size == expected_bytes and entry.compress_type == ZIP_STORED
            digest = hashlib.sha256()
            with archive.open(name) as source:
                for block in iter(lambda: source.read(1024 * 1024), b""):
                    digest.update(block)
            assert digest.hexdigest() == expected_hash
            model_info = {"path": name, "bytes": entry.file_size, "sha256": digest.hexdigest(), "uncompressed": True}
        reports.append({"flavor": flavor, "apk_bytes": apk.stat().st_size,
                        "permissions": permissions.strip().splitlines(), "native_libraries": libraries,
                        "model": model_info})
(ROOT / "docs/evidence/apk-inspection.json").write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
print(json.dumps(reports, indent=2))
