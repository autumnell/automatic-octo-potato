# Grimoire

An Android app for a Magic: The Gathering collection. Point the camera at a card and it's read, priced from Scryfall
and filed into the box its rules pick. The rest of the app is built around what you do with the cards after that:
boxes and binders, decks, a wishlist, prices over time, and a life counter for the table.

## What it does

- **Scan.** Reads the name bar and the collector line of a card held at any angle, then checks the art against a
  picture index when the text alone can't tell printings apart. Works offline from a card list kept on the phone.
  Modes for a pile (batch), a binder page or photo, a pack, a box check and a sort session.
- **Collection.** Boxes with rules ("red rares under $5"), auto-sort on scan, a move list of what to pull, Undo for
  every change, value by box and by color, and CSV import and export (ManaBox, Moxfield, Archidekt, or a full copy).
- **Decks.** Paste or build a list, see what you own and what's missing, bracket, mana base, goldfish odds, combos
  from Commander Spellbook, EDHREC suggestions, What to cut, and a playtest table.
- **Wishlist and prices.** Daily price refresh, target prices with a notification, and price history.
- **Game.** A table for 1 to 6 players with commander damage, poison and stats per deck.
- **Backups.** A zip of everything, weekly to a folder or one file on Drive, and restore by opening the zip.

## Install

Every merge to `main` builds an APK and publishes it on the
[latest release](https://github.com/autumnell/automatic-octo-potato/releases/tag/latest) as `mtg-scanner.apk`.
The debug key is in the repo, so each build installs over the last.

## Build

Kotlin and Jetpack Compose, CameraX with ML Kit text recognition, ONNX Runtime for the card finder, Room, OkHttp and
Coil. `./gradlew testDebugUnitTest` runs the tests, and `./gradlew assembleDebug` builds the app with JDK 17 and the
Android SDK. CI (`.github/workflows/build.yml`) runs the tests, a scan accuracy check on real photos, and screen
pictures in dark, light and large text for any pull request that changes the UI.

## Design

`design/Design.md` sets the look, and `design/design-tokens.json` holds its values; `DesignRulesTest` checks the
code against both. The default philosophy is [unslop](design/unslop/SKILL.md) by Damir Sokolovsky (CC BY 4.0).

## Credits

Card data and images come from [Scryfall](https://scryfall.com). Deck data from EDHREC, Commander Spellbook and MTGJSON.
Fonts and mana symbols are listed in [NOTICE.md](NOTICE.md). Magic: The Gathering is a trademark of Wizards of the
Coast; this app isn't made or endorsed by them.
