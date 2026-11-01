"""Turns open-card-collector's YOLO11n OBB card finder into an ONNX file the phone can run, and checks that the
app's way of reading its output (letterboxed 640 x 640 input, then centre, size and angle to corners) finds the
same corners as Ultralytics does.

Usage: export_yolo.py <out.onnx>
"""
import io, math, os, shutil, sys, urllib.request
import numpy as np
from PIL import Image
from huggingface_hub import hf_hub_download
from ultralytics import YOLO
import onnxruntime as ort

out = sys.argv[1]
pt = hf_hub_download("matteot11/collector-mtg-detector-yolo11n-obb", "best.pt")
model = YOLO(pt)
print("classes:", model.names)
path = model.export(format="onnx", imgsz=640, opset=12, simplify=True, dynamic=False, nms=False)
# Half precision halves the file the app carries; kept only when it finds the same corners.
import onnx
from onnxconverter_common import float16
half = out + ".fp16"
# Resize stays in full precision: the converter breaks its types otherwise.
onnx.save(float16.convert_float_to_float16(onnx.load(path), keep_io_types=True, op_block_list=["Resize"]), half)
sess = None

S = 640


def letterbox(img):
    """As the app does it: scaled to fit 640 x 640, top-left, the rest grey 114."""
    w, h = img.size
    s = S / max(w, h)
    nw, nh = round(w * s), round(h * s)
    canvas = Image.new("RGB", (S, S), (114, 114, 114))
    canvas.paste(img.resize((nw, nh), Image.BILINEAR), (0, 0))
    return canvas, s


def app_corners(img):
    """The app's decoding (CardFinderYolo.corners), in Python."""
    canvas, s = letterbox(img)
    a = (np.asarray(canvas, dtype=np.float32) / 255.0).transpose(2, 0, 1)[None]
    y = sess.run(None, {sess.get_inputs()[0].name: a})[0][0]  # channels x anchors
    c = y.shape[0]
    scores = y[4:c - 1].max(axis=0)
    best = int(scores.argmax())
    cx, cy, w, h, ang = y[0, best], y[1, best], y[2, best], y[3, best], y[c - 1, best]
    cos, sin = math.cos(ang), math.sin(ang)
    v1 = np.array([w / 2 * cos, w / 2 * sin])
    v2 = np.array([-h / 2 * sin, h / 2 * cos])
    ctr = np.array([cx, cy])
    q = np.array([ctr + v1 + v2, ctr + v1 - v2, ctr - v1 - v2, ctr - v1 + v2]) / s
    return float(scores[best]), q


def ul_corners(img):
    r = model.predict(img, imgsz=S, conf=0.05, verbose=False)[0]
    if r.obb is None or len(r.obb) == 0:
        return None
    b = int(r.obb.conf.argmax())
    return float(r.obb.conf[b]), r.obb.xyxyxyxy[b].cpu().numpy()


def match(a, b):
    """Largest corner distance, whichever corner each list starts at and either direction."""
    best = 1e9
    for bb in (b, b[::-1]):
        for k in range(4):
            best = min(best, np.linalg.norm(np.roll(bb, k, axis=0) - a, axis=1).max())
    return best


def check(file):
    """The worst corner difference against Ultralytics, as a share of the picture's height."""
    global sess
    sess = ort.InferenceSession(file, providers=["CPUExecutionProvider"])
    for i in sess.get_inputs():
        print("input", i.name, i.shape, i.type)
    for o in sess.get_outputs():
        print("output", o.name, o.shape, o.type)
    worst = 0.0
    for angle, size in ((0, (1200, 900)), (17, (900, 1200)), (-35, (1280, 960)), (60, (1000, 1000))):
        bg = Image.new("RGB", size, (60, 90, 70))
        c = card.resize((int(size[1] * 0.35), int(size[1] * 0.35 * 680 / 488))).rotate(angle, expand=True)
        bg.paste(c, ((size[0] - c.width) // 2, (size[1] - c.height) // 2), c)
        mine, theirs = app_corners(bg), ul_corners(bg)
        if theirs is None:
            print(angle, "ultralytics found nothing; app score", round(mine[0], 3))
            continue
        d = match(mine[1], theirs[1]) / size[1]
        worst = max(worst, d)
        print(f"angle {angle}: app score {mine[0]:.3f}, ultralytics {theirs[0]:.3f}, corners differ by {d * 100:.2f}% of the height")
    return worst


req = urllib.request.Request("https://api.scryfall.com/cards/named?exact=Lightning%20Bolt&format=image&version=normal",
                             headers={"User-Agent": "GrimoireExport/1.0", "Accept": "*/*"})
card = Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=30).read())).convert("RGBA")
for name, file in (("half precision", half), ("full precision", path)):
    try:
        w = check(file)
    except Exception as e:
        print(name, "doesn't load:", e)
        continue
    print(name, os.path.getsize(file), "bytes, worst", round(w * 100, 2), "%")
    if w <= 0.03:
        shutil.copy(file, out)
        print("OK:", name)
        break
else:
    sys.exit("The app's decoding doesn't match Ultralytics")
