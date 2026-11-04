"""Pictures of the scans the picture model missed badly (the right card far down its list), side by side, from the
accuracy report: app/build/scan-accuracy.md rows that name a scan file. Writes app/build/scan-misses.png."""
import glob, os, re, sys
from PIL import Image, ImageDraw

report = sys.argv[1] if len(sys.argv) > 1 else "app/build/scan-accuracy.md"
photos = sys.argv[2] if len(sys.argv) > 2 else "app/src/test/resources/scan-photos"
out = sys.argv[3] if len(sys.argv) > 3 else "app/build/scan-misses.png"
rows = []
for line in open(report, encoding="utf-8"):
    m = re.match(r"\| (.+?) \| (.+?) \(([0-9]+), best", line)
    if m and not m.group(2).startswith("right"):
        rows.append((m.group(1), m.group(2), m.group(3)))
if not rows:
    print("no far misses")
    sys.exit(0)
w, h, cols = 200, 300, 8
sheet = Image.new("RGB", (w * cols, h * ((len(rows) + cols - 1) // cols)), "white")
d = ImageDraw.Draw(sheet)
for i, (name, got, stamp) in enumerate(rows):
    files = glob.glob(os.path.join(photos, f"*_{stamp}.*"))
    x, y = (i % cols) * w, (i // cols) * h
    if files:
        sheet.paste(Image.open(files[0]).convert("RGB").resize((w, 280)), (x, y))
    d.text((x + 2, y + 282), f"{name[:28]}", fill="black")
sheet.save(out)
print("misses:", len(rows))
