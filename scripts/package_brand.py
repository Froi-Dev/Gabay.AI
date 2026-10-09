"""Size the supplied Gabay.AI artwork for Android; never generate a new logo."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
reference = Path(r"C:\Users\dever\OneDrive\Desktop\AppBuilders")
target = root / "app/src/main/res/drawable-nodpi"
target.mkdir(parents=True, exist_ok=True)
with Image.open(reference / "assets/Gabay.AI Brand Identity Presentation.png") as art:
    art.crop((69, 106, 525, 568)).resize((192, 192), Image.Resampling.LANCZOS).save(target / "gabay_mark.webp", quality=92)
