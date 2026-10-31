"""Sample pictures for ScanAccuracyTest, as plain pixels (Android's unit tests can't decode images).

For each of N random printings in the gallery: Scryfall's picture, clean, blurred, dark and glared at the card
model's size (320 x 448), and as a photo of the card lying turned on a table (600 x 800). Real scans from
app/src/test/resources/scan-photos are added as they are. Each file: width, height (big-endian ints), then RGB bytes.
"""
import io, json, random, struct, sys, time, glob, os, urllib.request
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

models, n, photos = sys.argv[1], int(sys.argv[2]), sys.argv[3]
out = os.path.join(models, "samples")
os.makedirs(out, exist_ok=True)
for f in glob.glob(os.path.join(out, "*")):
    os.remove(f)

def save(img, name):
    img = img.convert("RGB")
    with open(os.path.join(out, name + ".rgb"), "wb") as f:
        f.write(struct.pack(">ii", img.width, img.height))
        f.write(img.tobytes())

def glare(img):
    a = np.asarray(img).astype(np.float32)
    h, w, _ = a.shape
    yy, xx = np.mgrid[0:h, 0:w]
    g = np.exp(-(((xx - w * .6) / (w * .2)) ** 2 + ((yy - h * .35) / (h * .15)) ** 2)) * 180
    return Image.fromarray(np.clip(a + g[..., None], 0, 255).astype(np.uint8))

def on_table(img, rnd):
    w, h = 600, 800
    bg = tuple(rnd.randint(lo, lo + 50) for lo in (30, 60, 50))
    canvas = Image.new("RGB", (w, h), bg)
    scale = h * rnd.uniform(0.55, 0.75) / img.height
    card = img.resize((int(img.width * scale), int(img.height * scale)), Image.BILINEAR).convert("RGBA")
    card = card.rotate(rnd.uniform(-15, 15), resample=Image.BILINEAR, expand=True)
    x = (w - card.width) // 2 + rnd.randint(-50, 50)
    y = (h - card.height) // 2 + rnd.randint(-50, 50)
    canvas.paste(card, (x, y), card)
    return canvas

rnd = random.Random()
ids = json.load(open(os.path.join(models, "gallery_ids.json")))
got = 0
for cid in rnd.sample(ids, n * 2):
    if got >= n:
        break
    try:
        req = urllib.request.Request(f"https://api.scryfall.com/cards/{cid}?format=image&version=normal",
                                     headers={"User-Agent": "GrimoireScanTest/1.0", "Accept": "*/*"})
        img = Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=30).read())).convert("RGB")
    except Exception as e:
        print("skip", cid, e)
        continue
    time.sleep(0.12)
    small = lambda i: i.resize((320, 448), Image.BILINEAR)
    save(small(img), f"{cid}__Clean")
    save(small(img.filter(ImageFilter.GaussianBlur(1.5))), f"{cid}__Blurred")
    save(small(ImageEnhance.Brightness(img).enhance(0.5)), f"{cid}__Dark")
    save(small(glare(img)), f"{cid}__Glare")
    save(on_table(img, rnd), f"{cid}__Photo")
    got += 1
for p in glob.glob(os.path.join(photos, "*.jp*g")) + glob.glob(os.path.join(photos, "*.png")):
    save(Image.open(p), os.path.basename(p).split("_")[0] + "__Your scans")
print("samples:", got)
