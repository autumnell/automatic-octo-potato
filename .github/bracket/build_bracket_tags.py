"""Builds bracket-tags.json from Scryfall's community card tags (Tagger), for the app's bracket check.

Wizards publishes no card lists for mass land denial or tutors, only a definition, so Scryfall's oracle tags stand in.
Prints how many cards each candidate tag has, so a renamed tag shows up in the log.
"""
import json, sys, time, urllib.parse, urllib.request

CANDIDATES = {
    "tutor": ["tutor"],
    "mld": ["mass-land-denial"],
}


def search(q):
    url = "https://api.scryfall.com/cards/search?" + urllib.parse.urlencode({"q": q, "unique": "cards"})
    names = []
    while url:
        req = urllib.request.Request(url, headers={"User-Agent": "Grimoire-app/1.0", "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                page = json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return []
            raise
        names += [c["name"] for c in page.get("data", [])]
        url = page.get("next_page") if page.get("has_more") else None
        time.sleep(0.12)
    return sorted(set(names))


def main(out):
    result = {}
    for key, tags in CANDIDATES.items():
        found = set()
        for t in tags:
            names = search(f"otag:{t} game:paper")
            print(f"otag:{t}: {len(names)} cards; e.g. {names[:6]}")
            found |= set(names)
        result[key] = sorted(found)
    # Land-only searches (ramp) are left in: the app drops them by card text, so a card that finds "a creature or
    # land card" (Traverse the Ulvenwald) still counts as a tutor.
    print({k: len(v) for k, v in result.items()})
    with open(out, "w") as f:
        json.dump(result, f, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1])
