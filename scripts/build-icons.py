"""Convert the generated icon to application PNG/Windows ICO assets. Requires Pillow."""
from pathlib import Path

from PIL import Image


project = Path(__file__).resolve().parent.parent
source = project / "assets/branding/app-icon-source.png"
png = project / "composeApp/src/commonMain/composeResources/drawable/app_icon.png"
ico = project / "composeApp/src/desktopMain/resources/app-icon.ico"
png.parent.mkdir(parents=True, exist_ok=True)
ico.parent.mkdir(parents=True, exist_ok=True)
with Image.open(source) as image:
    rgba = image.convert("RGBA")
    rgba.resize((512, 512), Image.Resampling.LANCZOS).save(png)
    rgba.save(ico, sizes=[(size, size) for size in (16, 24, 32, 48, 64, 128, 256)])
print(f"Application PNG: {png}")
print(f"Windows ICO: {ico}")
