"""Grimoire's card models against open-card-collector's, on the same pictures.

Picture models: Grimoire's card model (card.onnx) and open-card-collector's DINOv3 Small embedder, each finding the
sample cards among a gallery of the sample cards plus random other printings (Scryfall's pictures). Card finders:
Grimoire's corner model (corners.onnx) and open-card-collector's YOLO11n OBB detector, on the "Photo" samples (a
card lying turned on a table), against where its corners really are (samples/corners.json).

Usage: compare_models.py <scan-models dir> <distractors> <report.md>
Expects card.onnx, corners.onnx, gallery_ids.json and samples/ from scan_samples.py in the models folder.
"""
import io, json, math, os, random, struct, sys, time, urllib.request
import numpy as np
from PIL import Image

models, n_distract, report = sys.argv[1], int(sys.argv[2]), sys.argv[3]
samples = os.path.join(models, "samples")
lines = []


def say(s=""):
    print(s)
    lines.append(s)


def read_rgb(path):
    with open(path, "rb") as f:
        w, h = struct.unpack(">ii", f.read(8))
        return Image.frombytes("RGB", (w, h), f.read(w * h * 3))


def scryfall(cid):
    req = urllib.request.Request(f"https://api.scryfall.com/cards/{cid}?format=image&version=normal",
                                 headers={"User-Agent": "GrimoireModelCompare/1.0", "Accept": "*/*"})
    img = Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=30).read())).convert("RGB")
    time.sleep(0.11)
    return img


def unit(v):
    v = np.asarray(v, dtype=np.float32).reshape(-1)
    return v / (np.linalg.norm(v) + 1e-9)


# --- The models -------------------------------------------------------------------------------------------------

import onnxruntime as ort

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
card_sess = ort.InferenceSession(os.path.join(models, "card.onnx"), providers=["CPUExecutionProvider"])
pose_sess = ort.InferenceSession(os.path.join(models, "corners.onnx"), providers=["CPUExecutionProvider"])


def feed(sess, a):
    """Input in the type the model wants (the fp16 models want float16)."""
    i = sess.get_inputs()[0]
    return {i.name: a.astype(np.float16 if "float16" in i.type else np.float32)}


def ours_embed(img):
    a = np.asarray(img.resize((320, 448), Image.BILINEAR), dtype=np.float32) / 255.0
    a = ((a - MEAN) / STD).transpose(2, 0, 1)[None].astype(np.float32)
    return unit(card_sess.run(None, feed(card_sess, a))[0].astype(np.float32))


def order(q):
    """Corners top-left first, clockwise (as CardOutline.order)."""
    pts = np.asarray(q, dtype=np.float64).reshape(4, 2)
    c = pts.mean(axis=0)
    ang = np.arctan2(pts[:, 1] - c[1], pts[:, 0] - c[0])
    pts = pts[np.argsort(ang)]
    start = int(np.argmin(pts[:, 0] + pts[:, 1]))
    return np.roll(pts, -start, axis=0)


def ours_corners(img):
    w, h = img.size
    a = (np.asarray(img.resize((640, 640), Image.BILINEAR), dtype=np.float32) / 255.0).transpose(2, 0, 1)[None]
    outs = pose_sess.run(None, feed(pose_sess, a))
    named = {o.name: np.asarray(v, dtype=np.float32) for o, v in zip(pose_sess.get_outputs(), outs)}
    scores = np.asarray(named["scores"]).reshape(-1)
    kps = np.asarray(named["keypoints"]).reshape(-1)
    if scores.size == 0:
        return None
    best = int(np.argmax(scores))
    if 1 / (1 + math.exp(-float(scores[best]))) < 0.6:
        return None
    q = kps[best * 8: best * 8 + 8].reshape(4, 2) * np.array([w, h])
    return order(q)


import torch
from transformers import AutoModel, AutoProcessor

DINO = "matteot11/collector-mtg-embedder-dinov3-small"
dino_proc = AutoProcessor.from_pretrained(DINO)
dino = AutoModel.from_pretrained(DINO).eval()


def dino_embed(img):
    inputs = dino_proc(images=img, return_tensors="pt")
    with torch.inference_mode():
        v = dino(**inputs).last_hidden_state[:, 0]
    return unit(v.numpy())


from huggingface_hub import hf_hub_download
from ultralytics import YOLO

yolo = YOLO(hf_hub_download("matteot11/collector-mtg-detector-yolo11n-obb", "best.pt"))


def yolo_corners(img):
    r = yolo.predict(img, imgsz=640, conf=0.25, verbose=False)[0]
    if r.obb is None or len(r.obb) == 0:
        return None
    best = int(r.obb.conf.argmax())
    return order(r.obb.xyxyxyxy[best].cpu().numpy())


def warp(img, q, w=320, h=448):
    """The quad q of img drawn straight at w x h."""
    q = order(q)
    # PIL's QUAD takes the source corners: top-left, bottom-left, bottom-right, top-right.
    data = [q[0][0], q[0][1], q[3][0], q[3][1], q[2][0], q[2][1], q[1][0], q[1][1]]
    return img.transform((w, h), Image.QUAD, data=[float(v) for v in data], resample=Image.BILINEAR)


# --- The pictures -----------------------------------------------------------------------------------------------

truth = json.load(open(os.path.join(samples, "corners.json")))
files = sorted(f for f in os.listdir(samples) if f.endswith(".rgb"))
targets = sorted({f.split("__")[0] for f in files if f.endswith("__Clean.rgb")})
ids = json.load(open(os.path.join(models, "gallery_ids.json")))
rnd = random.Random(7)
others = rnd.sample([i for i in ids if i not in set(targets)], n_distract * 2)

gallery_ids, gallery_imgs = [], []
for cid in targets + others:
    if len(gallery_ids) >= len(targets) + n_distract:
        break
    try:
        gallery_imgs.append(scryfall(cid))
        gallery_ids.append(cid)
    except Exception as e:
        print("skip", cid, e)
missing = [t for t in targets if t not in gallery_ids]

say("### Picture models: Grimoire's against open-card-collector's DINOv3 Small")
say()
say(f"Each sample card is looked for among {len(gallery_ids)} printings (the samples and random others). "
    "Right means the best match is the card itself.")
say()

embedders = {"Grimoire card model": ours_embed, "DINOv3 Small (open-card-collector)": dino_embed}
gal = {}
for name, fn in embedders.items():
    t0 = time.time()
    gal[name] = np.stack([fn(i) for i in gallery_imgs])
    print(name, "gallery", round(time.time() - t0, 1), "s")


def find(name, vec):
    s = gal[name] @ vec
    order_ = np.argsort(-s)
    return gallery_ids[order_[0]], float(s[order_[0]] - s[order_[1]])


kinds = {}
for f in files:
    cid, kind = f[:-4].split("__")
    if cid in missing or kind == "Your scans":
        continue
    img = read_rgb(os.path.join(samples, f))
    if kind == "Photo":
        # The card straightened where it really is: compares the picture models alone.
        if cid not in truth:
            continue
        img = warp(img, truth[cid])
        kind = "Photo (true corners)"
    kinds.setdefault(kind, []).append((cid, img))

say("| Pictures | " + " | ".join(f"{n}: right, ms" for n in embedders) + " |")
say("|---|" + "---|" * len(embedders))
for kind, items in kinds.items():
    row = [kind]
    for name, fn in embedders.items():
        right, ms = 0, 0.0
        for cid, img in items:
            t0 = time.time()
            v = fn(img)
            ms += (time.time() - t0) * 1000
            hit, _ = find(name, v)
            right += hit == cid
        row.append(f"{right} of {len(items)} ({right * 100 // max(1, len(items))}%), {ms / max(1, len(items)):.0f}")
    say("| " + " | ".join(row) + " |")

say()
say("### Card finders: Grimoire's corner model against open-card-collector's YOLO11n OBB")
say()
say("On the Photo samples. Found within 3% means every corner lands within 3% of the card's height of where it is; "
    "Right means the card it finds, straightened and looked up with Grimoire's card model, is the right card.")
say()
say("| Finder | Found | Found within 3% | Mean corner error | Right | ms each |")
say("|---|---|---|---|---|---|")
photos = [(f.split("__")[0], read_rgb(os.path.join(samples, f))) for f in files if f.endswith("__Photo.rgb")]
photos = [(c, i) for c, i in photos if c in truth and c not in missing]
for name, fn in {"Grimoire corner model": ours_corners, "YOLO11n OBB (open-card-collector)": yolo_corners}.items():
    found = close = right = 0
    errs, ms = [], 0.0
    for cid, img in photos:
        t0 = time.time()
        q = fn(img)
        ms += (time.time() - t0) * 1000
        if q is None:
            continue
        found += 1
        true = order(truth[cid])
        height = np.linalg.norm(true[3] - true[0])
        # Either way up counts: the finders don't know which end of the card is the top.
        e = min(np.linalg.norm(np.roll(q, k, axis=0) - true, axis=1).max() for k in (0, 2)) / height
        errs.append(e)
        close += e <= 0.03
        straight = warp(img, q)
        hits = [find("Grimoire card model", ours_embed(s))[0] for s in (straight, straight.rotate(180))]
        right += cid in hits
    n = max(1, len(photos))
    mean = f"{np.mean(errs) * 100:.1f}%" if errs else "-"
    say(f"| {name} | {found} of {len(photos)} | {close} | {mean} | {right} of {len(photos)} ({right * 100 // n}%) | {ms / n:.0f} |")

if missing:
    say()
    say(f"{len(missing)} sample cards couldn't be downloaded and were left out.")

with open(report, "w") as f:
    f.write("\n".join(lines) + "\n")
