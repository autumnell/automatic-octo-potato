"""Turns Commander Spellbook's variants file into the app's small combo list.

One line per combo, tab-separated: Spellbook id, card names joined by "|", and up to three results joined by "|".
Only combos whose every piece is a named card (no "any creature with..." templates) are kept, since only those
can be checked against a collection. Usage: build_combos.py variants.json combos.tsv.gz
"""
import gzip
import json
import sys

src, out = sys.argv[1], sys.argv[2]
with open(src, encoding="utf-8") as f:
    variants = json.load(f)["variants"]

kept = 0
with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as w:
    for v in variants:
        if v.get("status") != "OK" or v.get("requires"):
            continue
        cards = [u["card"]["name"] for u in v.get("uses", []) if u.get("card", {}).get("name")]
        if not 2 <= len(cards) <= 5:
            continue
        makes = [p["feature"]["name"] for p in v.get("produces", []) if p.get("feature", {}).get("name")][:3]
        clean = lambda s: s.replace("\t", " ").replace("|", "/").replace("\n", " ")
        w.write(f'{v["id"]}\t{"|".join(map(clean, cards))}\t{"|".join(map(clean, makes))}\n')
        kept += 1
print(f"{kept} combos of {len(variants)} variants")
