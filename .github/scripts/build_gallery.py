"""Builds the card picture gallery that goes inside the app: every paper printing's fingerprint from the card picture
model, in the app's own gallery format (CardGallery.Codec, "GCG1").

It starts from the last gallery built (or the published one on Hugging Face the first time) and adds every
printing Scryfall has that it lacks, from Scryfall's pictures, the same way the phone does (CardPrints.fingerprint):
the whole card picture squashed to 320 x 448, ImageNet colour scaling, the model's 256 numbers made unit length.

usage: build_gallery.py <models dir> <previous gallery.bin or ""> <out gallery.bin> [max new]
The models dir has card.onnx and, when there is no previous gallery, gallery_ids.json, meta.json and
gallery_f16.bin.
"""
import concurrent.futures
import gzip
import io
import json
import os
import struct
import sys
import time
import urllib.request

import numpy as np
import onnxruntime as ort
from PIL import Image

MAGIC = 0x47434731
DIM = 256
UA = {"User-Agent": "GrimoireGalleryBuilder/1.0", "Accept": "application/json"}
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


def java_utf(s):
    """Java's DataOutputStream.writeUTF: modified UTF-8 of each UTF-16 unit, after a 2-byte length."""
    out = bytearray()
    units = struct.unpack(">%dH" % (len(s.encode("utf-16-be")) // 2), s.encode("utf-16-be"))
    for u in units:
        if 0 < u < 0x80:
            out.append(u)
        elif u < 0x800:
            out += bytes([0xC0 | (u >> 6), 0x80 | (u & 0x3F)])
        else:
            out += bytes([0xE0 | (u >> 12), 0x80 | ((u >> 6) & 0x3F), 0x80 | (u & 0x3F)])
    return struct.pack(">H", len(out)) + bytes(out)


def read_java_utf(f):
    n = struct.unpack(">H", f.read(2))[0]
    b = f.read(n)
    units, i = [], 0
    while i < len(b):
        c = b[i]
        if c < 0x80:
            units.append(c); i += 1
        elif c < 0xE0:
            units.append(((c & 0x1F) << 6) | (b[i + 1] & 0x3F)); i += 2
        else:
            units.append(((c & 0x0F) << 12) | ((b[i + 1] & 0x3F) << 6) | (b[i + 2] & 0x3F)); i += 3
    return struct.pack(">%dH" % len(units), *units).decode("utf-16-be", errors="replace")


def read_gallery(path):
    with open(path, "rb") as f:
        if struct.unpack(">i", f.read(4))[0] != MAGIC:
            raise ValueError("not a gallery")
        n = struct.unpack(">i", f.read(4))[0]
        rows = [tuple(read_java_utf(f) for _ in range(4)) for _ in range(n)]
        scales = np.frombuffer(f.read(4 * n), dtype=">f4").astype(np.float32)
        vecs = np.frombuffer(f.read(n * DIM), dtype=np.int8).reshape(n, DIM)
    return rows, scales, vecs


def write_gallery(path, rows, scales, vecs):
    with open(path + ".part", "wb") as f:
        f.write(struct.pack(">ii", MAGIC, len(rows)))
        for r in rows:
            for s in r:
                f.write(java_utf(s))
        f.write(np.asarray(scales, dtype=">f4").tobytes())
        f.write(np.asarray(vecs, dtype=np.int8).tobytes())
    os.replace(path + ".part", path)


def label(s):
    """CardGallery.Published.label: "Lightning Bolt | m10 #146" as name, set, number."""
    bar = s.rfind(" | ")
    if bar < 0:
        return s, "", ""
    rest = s[bar + 3:]
    h = rest.find(" #")
    if h < 0:
        return s[:bar], rest.lower(), ""
    return s[:bar], rest[:h].lower(), rest[h + 2:]


def quantize(v):
    """CardGallery.quantizeInto: signed bytes and the scale that turns them back."""
    m = float(np.max(np.abs(v)))
    if m == 0:
        return np.zeros(DIM, np.int8), 0.0
    q = np.clip(np.floor(v * (127.0 / m) + 0.5), -127, 127).astype(np.int8)
    return q, m / 127.0


def published(models):
    ids = json.load(open(os.path.join(models, "gallery_ids.json")))
    meta = json.load(open(os.path.join(models, "meta.json")))
    halves = np.fromfile(os.path.join(models, "gallery_f16.bin"), dtype="<f2").astype(np.float32).reshape(len(ids), DIM)
    rows, scales, vecs = [], np.zeros(len(ids), np.float32), np.zeros((len(ids), DIM), np.int8)
    for i, cid in enumerate(ids):
        name, s, num = label(meta.get(cid, ""))
        rows.append((cid, name, s, num))
        vecs[i], scales[i] = quantize(halves[i])
    return rows, scales, vecs


def get(url, tries=4):
    for t in range(tries):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()
        except Exception:
            if t == tries - 1:
                raise
            time.sleep(2 ** t)


def scryfall_cards():
    """Every paper printing with a picture, newest first: (id, name, set, number, picture url)."""
    bulk = json.loads(get("https://api.scryfall.com/bulk-data"))
    entry = next(d for d in bulk["data"] if d["type"] == "default_cards")
    url = entry.get("download_uri") or entry.get("jsonl_download_uri")
    raw = get(url)
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    text = raw.decode("utf-8")
    cards = json.loads(text) if text.lstrip().startswith("[") else [json.loads(l) for l in text.splitlines() if l.strip()]
    out = []
    for c in cards:
        if c.get("digital") or "paper" not in c.get("games", ["paper"]) or c.get("layout") == "art_series":
            continue
        img = (c.get("image_uris") or {}).get("normal") or ((c.get("card_faces") or [{}])[0].get("image_uris") or {}).get("normal")
        if not img:
            continue
        out.append((c.get("released_at", ""), c["id"], c["name"], c["set"].lower(), c.get("collector_number", ""), img))
    out.sort(reverse=True)
    return [o[1:] for o in out]


def main():
    models, prev, out = sys.argv[1], sys.argv[2], sys.argv[3]
    most = int(sys.argv[4]) if len(sys.argv) > 4 else 20000
    if prev and os.path.exists(prev):
        rows, scales, vecs = read_gallery(prev)
        print(f"Previous gallery: {len(rows)} printings")
    else:
        rows, scales, vecs = published(models)
        print(f"Published gallery: {len(rows)} printings")
    have = {r[0] for r in rows}
    missing = [c for c in scryfall_cards() if c[0] not in have][:most]
    print(f"Missing: {len(missing)}")

    sess = ort.InferenceSession(os.path.join(models, "card.onnx"), providers=["CPUExecutionProvider"])
    inp = sess.get_inputs()[0]
    half = inp.type == "tensor(float16)"

    def fetch(c):
        try:
            return c, Image.open(io.BytesIO(get(c[4]))).convert("RGB")
        except Exception as e:
            print(f"skip {c[1]} ({c[0]}): {e}")
            return c, None

    new_rows, new_scales, new_vecs = [], [], []
    with concurrent.futures.ThreadPoolExecutor(12) as pool:
        for k, (c, img) in enumerate(pool.map(fetch, missing)):
            if img is None:
                continue
            x = np.asarray(img.resize((320, 448), Image.BILINEAR), dtype=np.float32) / 255.0
            x = ((x - MEAN) / STD).transpose(2, 0, 1)[None]
            y = sess.run(None, {inp.name: x.astype(np.float16 if half else np.float32)})[0].astype(np.float32).reshape(-1)
            n = float(np.linalg.norm(y))
            q, s = quantize(y / n if n else y)
            new_rows.append(c[:4]); new_scales.append(s); new_vecs.append(q)
            if k % 500 == 0:
                print(f"{k} of {len(missing)}")
    if new_rows:
        rows = rows + new_rows
        scales = np.concatenate([scales, np.asarray(new_scales, np.float32)])
        vecs = np.concatenate([vecs, np.stack(new_vecs)])
    write_gallery(out, rows, scales, vecs)
    print(f"Added {len(new_rows)}; gallery now {len(rows)} printings")


if __name__ == "__main__":
    main()
