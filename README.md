# MTG Scanner

An Android app that scans Magic: The Gathering cards with the camera, identifies them on
[Scryfall](https://scryfall.com), and keeps your collection organized.

## Install on your phone

1. On your phone, open this repository's **Releases** page and download `mtg-scanner.apk`
   from the **latest** release.
2. Open the file. Android will ask you to allow installs from your browser the first time.
3. Open **MTG Scanner** and allow the camera.

Each new build replaces the "latest" release, and installs cleanly over the previous version.

## Using it

- **Home:** collection value, card counts, recently added and most valuable cards, and a color breakdown.

- **Scan tab:** hold a card in front of the camera. It can be sideways or upside down.
  The app reads the name (and on cards from 2020 on, the set code and collector number in
  the bottom-left corner) and looks it up on Scryfall.
  - **Auto-add off** (default): the scanner stops on each card so you can check it, pick a
    different printing, then tap **Add** (or **+4** for a playset).
  - **Auto-add on:** confident matches are added straight away, so you can feed cards one
    after another. Take the card out of view before scanning another copy of the same card.
  - **Foil** marks the next cards as foil. **Undo** removes the last one added.
- **Search tab:** search by name (Scryfall syntax like `t:dragon c:r` works too) and see results as a
  card grid, or browse whole sets from the **Sets** tab. Tap a card to pick foil, quantity and box, then add it.
- **Collection tab:** totals, then your **boxes** (Bulk plus any boxes, binders or decks you create).
  Tap a box, or "Browse entire collection", to see the cards as a grid or list. There you can search by name, type, rules text or tag; filter by color, rarity, type,
  set and foil; sort by name, price, mana value, set number, color or date added; group by
  set, color, type or rarity. The refresh button pulls current prices from Scryfall, and the
  download button exports a CSV that most collection sites can import.
- Tap a card to change quantity, foil, condition, which box it's in, add notes, or open it on Scryfall.
- **Settings** (gear on Home, or the menu on Collection): dark, light or system theme, and the accent
  color, from a preset list or any color with the picker.

## What gets recorded

Name, set, collector number, rarity, type line, mana cost and value, colors and color
identity, rules text, artist, release date, USD price (normal and foil), image, Scryfall link,
plus tags built from Scryfall data: keywords, card types and subtypes, and format legality
(standard, pioneer, modern, commander, pauper).

## How scanning works

Frames from CameraX go to ML Kit's on-device text recognizer. Because a card on a table can
face any way, each frame is read at the orientation that worked last time, and if that isn't a
clean read, at the other three quarter turns; the most readable result wins
(`scan/CardTextAnalyzer.kt`). `scan/CardTextParser.kt` then picks out the name and the footer's
set code and collector number. Two frames have to agree before Scryfall is queried, and lookups
go exact printing first, then fuzzy name in that set, then fuzzy name anywhere
(`data/CollectionRepository.kt`).

## Building

Kotlin, Jetpack Compose, CameraX, ML Kit, Room, OkHttp. Open the project in Android Studio,
or run `./gradlew assembleDebug`. GitHub Actions builds and tests every push.

The repo includes a debug signing key (`keystore/debug.keystore`) so every build can update
the previous install. It's fine for a personal app; use a private key before publishing to
the Play Store.
