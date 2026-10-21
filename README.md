# Grimoire

An Android app that scans Magic: The Gathering cards with the camera, identifies them on
[Scryfall](https://scryfall.com), and keeps your collection organized.

## Install on your phone

1. On your phone, open this repository's **Releases** page and download `mtg-scanner.apk`
   from the **latest** release.
2. Open the file. Android will ask you to allow installs from your browser the first time.
3. Open **Grimoire** and allow the camera.

Each new build replaces the "latest" release, and installs cleanly over the previous version.

## Using it

- **Boxes come first.** Before scanning, create a box. A box can take any card, or have rules:
  colors (including multicolor and colorless), rarity, printing (borderless, showcase, extended
  art, full art, etched, promo, retro frame), foil or non-foil, card type, price range and set.
  There are ready-made starting points like Bulk (commons and uncommons under $1), Rares &
  Mythics, Borderless & Showcase, Foils, High value and one per color.
  Making a box takes three steps: start from a ready-made box or a blank one, pick its name,
  kind and color, then turn on rules (each rule is a card you can open and close). A live
  preview shows how many of your cards fit, and warns when a box earlier in the order takes
  some of them first.
- **Auto-sort order:** Collection › Auto-sort order sets which box is checked first, and
  picks a fallback box for cards no rules fit.
- **Home:** collection value with its change since the cards were added (like "+$4.20 (3.1%)"),
  a value chart over time, recently added, most valuable, biggest gains, and a color breakdown.
  Prices refresh once a day when the app opens.
- **Scan tab:** hold a card in front of the camera. It can be sideways or upside down.
  - The header picks where cards go: **Auto-sort** puts each card in the first box whose rules
    fit, or pick one box. You can create a box right from that menu.
  - Only text inside the frame is read. Names are corrected against Scryfall's full list of
    card names, kept on the phone and refreshed weekly, so lookups are quicker and more exact.
  - Cards without a readable set code and collector number (older frames, foreign or foil cards)
    get their printing picked by comparing the artwork with each printing's art.
  - Offline, a clearly read card can be saved for later and is added once you're back online.
  - If a scanned card is on your wishlist, the scanner says so and ticks it off when you add it.
  - **Auto-add off** (default): the scanner stops on each card so you can check it, pick a
    different printing, then tap **Add** (or **+4** for a playset). **Auto-add on** adds
    confident matches straight away. **Foil** marks the next cards as foil.
- **Search tab:** search by name (Scryfall syntax like `t:dragon c:r` works too) or browse whole
  sets. Tap a card to pick foil, quantity and box (or Auto-sort), or add it to your wishlist.
- **Collection tab** has three parts:
  - **Collection:** totals and your boxes, each showing its rules. Edit a box's name and rules
    from its menu. Deleting a box asks where its cards go: an "Anything" box (made if you don't have
    one), your other boxes by their rules, one box, or deleted permanently (which asks again first).
    **Sort cards out of this box** (box menu) sorts a catch-all box later: into a new box set up
    with the usual rules, or into your other boxes in Auto-sort order. Tap a box, or "Browse entire collection", to search, filter (color,
    rarity, type, set, foil, printing, and role like Removal, Ramp or Card draw), sort (including
    price change) and group.
  - **Decks:** paste a decklist from Moxfield, Archidekt, Arena or MTGO. You see what you own,
    which box each card is in, what's missing and what the missing cards cost, and can add the
    missing cards to your wishlist.
  - **Lists:** your wishlist and trade binders (boxes of kind "Trade binder"), plus tools:
    **Set tracker** (how much of each set you own, the missing cards and their cost, add missing
    to wishlist), **Extras finder** (copies beyond 4, or 1 for Commander, moved to a trade binder)
    and **Pack history**.
  - Browsing: long-press cards to select several, then move, tag or delete them together. In list
    view, swipe right to move a card or left to delete it. Searches can be saved, and recent ones
    show as chips. Adds, moves and deletes show an **Undo** bar.
- Tap a card to change quantity, foil, condition, box, notes and your own tags, see its price
  change since you added it with a price history chart, and what it does (Scryfall's community tags).
- **Scanner modes** (grid button over the camera): **Open a pack** tracks a pack's value as you
  scan it and saves the result to Pack history; **Binder page** reads a 9-pocket page at once and
  lets you tick which cards to add.
- **Game tab:** a life counter for 1 to 6 players. Pick a format (Commander, Standard, Brawl,
  Oathbreaker, Two-Headed Giant, Limited, custom), starting life, names and a table style (bold,
  gradient or dark). Tap the sides of a panel for -1/+1, hold for ±10. Each player's menu opens
  full screen facing them, in big tiles with no scrolling: counters (life, poison, commander tax,
  energy, experience, rad), commander damage from each opponent (and partners), and player
  settings (name, color, card art from your collection behind the life total, monarch,
  initiative, turn, concede). Round buttons by the middle button set the monarch, initiative and
  day/night, and undo. The middle button opens turns, first player, dice, history, restart and
  end game. A player is out at 0 life, 10 poison or 21 commander damage. Finished games are saved
  to **Game stats** with wins per player.
- **Home screen widget** shows your collection value with a Scan button. A short tour shows on
  first run (Settings › Show the tour again).
- **Scan settings** (scanner gear, or Settings › Scanning):
  - **Auto-add** adds cards by itself when it's sure enough: exact printing only, a confident
    name, or any good match. Unsure cards can pause and ask, be set aside under a "Check"
    button, or be skipped.
  - **Skip rules** count but don't add basic lands, tokens or cards under a price.
  - **Feedback:** pick the sound for an add and a different one for "set aside", plus vibrate
    and frame flash.
  - **Presets** such as "Sorting bulk" and "Valuing a binder" save all of this, and switch from
    the scan header. A big "Put it in …" label in the box's color shows where the card goes. The scan list can be grouped by box for filing a pile.
- **Decks tab** also suggests **Commander ideas**: legendary creatures you own, ranked by how many of
  your cards fit their colors. Card pages show format legality and color identity.
- **Lists tab** has a **trade calculator** (what you give vs. get, then apply it to your collection)
  and share buttons for trade binders and the wishlist.
- **Settings:** theme and accent color (Gold by default); **Prices**: hide every price in the app,
  or get daily **price alerts** when a card you own or want moves by a percentage and dollar amount
  you choose; scan sound; plus **Your data**: import a CSV from
  ManaBox, Moxfield, Archidekt, Deckbox or Dragon Shield; back up everything to a file and
  restore it; refresh card roles.
- **Scan log (hidden):** tap the version number in Settings seven times. Settings then has a
  Scan log page listing what the scanner read and matched for the last 500 lookups, with a share
  button, so scanning problems can be sent and fixed.

## What gets recorded

Name, set, collector number, rarity, type line, mana cost and value, colors and color
identity, rules text, artist, release date, USD price (normal and foil), image, Scryfall link,
plus tags built from Scryfall data: keywords, card types and subtypes, and format legality
(standard, pioneer, modern, commander, pauper). Also the price when the card was added, its
printing treatments (borderless, showcase…), and roles from Scryfall's community tags.

## How scanning works

Frames from CameraX go to ML Kit's on-device text recognizer. Because a card on a table can
face any way, each frame is read at the orientation that worked last time, and if that isn't a
clean read, at the other three quarter turns; the most readable result wins
(`scan/CardTextAnalyzer.kt`). `scan/CardTextParser.kt` then picks out the name and the footer's
set code and collector number, and where the artwork is. Names are snapped to the nearest real
card name with `scan/CardNameMatcher.kt`. Two frames have to agree before Scryfall is queried, and
lookups go exact printing first, then the corrected name in that set, then anywhere
(`data/CollectionRepository.kt`). Without a footer, a small "difference hash" of the artwork
(`scan/ImageHash.kt`, `scan/ArtMatcher.kt`) is compared with every printing's art to pick the right one.

On a steady frame, OpenCV (`scan/CvCards.kt`) finds the card's four corners and straightens the name bar,
footer and art; if OpenCV won't load, the app's own corner fit (`scan/CardQuad.kt`) does. Names printed in
Japanese, Chinese or Korean are read by ML Kit's CJK readers (only when the Latin reader finds no name, or
that language is picked in the scan bar) and matched against Scryfall's printed names (`scan/CjkNames.kt`).
The art model (Standard or Advanced, picked in scan settings) is downloaded on first use.

## Building

Kotlin, Jetpack Compose, CameraX, ML Kit, OpenCV, MediaPipe, Room, OkHttp. Open the project in Android Studio,
or run `./gradlew assembleDebug`. GitHub Actions builds and tests every push.

The repo includes a debug signing key (`keystore/debug.keystore`) so every build can update
the previous install. It's fine for a personal app; use a private key before publishing to
the Play Store.
