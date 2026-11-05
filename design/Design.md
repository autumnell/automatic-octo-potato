# Grimoire Design System

Direction **A · Grimoire**, chosen by Rae on 2026-11-04. Built with the
[design-system skill](https://github.com/Jaywalker-not-a-whitewalker/DesignSystem) (tokens first, 8pt grid,
atoms → molecules → organisms), and checked against the
[AI-generated look checklist](https://aitoolpick.org/blog/ai-generated-website-checklist/).

**Default philosophy: [unslop](unslop/SKILL.md)** by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), CC BY 4.0
(Rae, 2026-11-05). Quiet chrome, one accent, and the richness kept for one or two moments of real content. Its nine
passes (spacing, color, icons, copy, type, layout, details, visuals, motion) and its [sign list](unslop/references/signs.md)
apply to every screen. This file records how they apply here, and the few places Grimoire departs from them on
purpose ("Where Grimoire departs from unslop" below). Anything this file doesn't settle, unslop does.

## Direction

The five lines unslop asks for, taken from what the app does.

- **Product.** A phone app that scans a Magic collection into the right boxes and keeps track of what it's worth.
- **Character.** Archival, exact, a little warm.
- **World.** A collector's table: leather binders, sleeves, printed card frames, ink on paper.
- **Moments.** The card art (the Newest strip, the card page, box covers) and the collection value on Home. The chrome
  around them stays quiet.
- **Signature.** Fraunces headings, a soft old-style serif like the title page of an old book. Nowhere else.

`design-tokens.json` holds the values. The code reads them from `ui/theme/Theme.kt` (`Palette`, `Space`, `Corners`,
typography). Change both together. Mockups: the "Grimoire redesign directions" canvas.

## Overview

| | |
|---|---|
| Form factor | Android phone app (Compose) |
| App type | Tool: a card collection manager |
| User | A Magic collector with cards on the table and the phone in one hand |
| Core action | Scan cards into the right box |
| Personality | Warm, archival, precise |

## Color

Three surfaces, each a clear step lighter than the one below. Rae asked for clear contrast between panels and the
background.

| Token | Dark | Light | Use |
|---|---|---|---|
| `Background` | #100F0E | #ECEBE8 | Screen |
| `Surface` | #1E1C19 | #FFFFFF | Panels, sheets, dialogs |
| `SurfaceHigh` | #2B2824 | #E0DFDB | Chips, inputs, pressed rows |
| `Text` | #ECE8E1 | #1C1B19 | Primary text |
| `Muted` | #A29C92 | #5B5955 | Secondary text (4.6:1 or better) |
| `Faint` | #77716A | #868480 | Disabled labels only. It is under 4.5:1, so never for text you must read |
| `Rule` | #332F2A | #D2D1CC | 1dp lines under rows and tabs |
| `Accent` | #9E3A33 | #8A2F29 | Oxblood. The user can change it |
| `AccentText` | computed | computed | Accent moved to 4.6:1 on the background, for text |
| `Success` / `Danger` / `Gold` / `Info` | | | Price up / down, foil and warnings, links in help |

Always-dark surfaces (the camera, card art, the game table) use `Stage`, `StagePanel`, `ArtBase` and the
`OnDark*` colors in both modes. Colors with a meaning in Magic (mana, rarity, foil, player colors, dice) are not
themed; they stay next to the component that draws them.

Rules:
- The accent is only for the main button on a screen, Scan, the selected tab and badges. Links, text buttons,
  switches, checkboxes and charts are ink (`Text`/`Muted`): Material's `primary` is set to `Text` so nothing turns
  red by default. It is never used for decoration, headings or borders.
- Gains and losses always show a sign (+/−) as well as a color, so they don't rely on red or green alone.
- No gradients on buttons or backgrounds. Gradients are allowed only as scrims, to keep text readable over card art
  or the camera.
- No colored side stripes on cards or rows.
- No color washes: pages don't take a tint from card art. The art itself carries the color.
- No colored glows. Shadows are neutral and small; Scan's raised circle has a 4dp black shadow, not an accent glow.
- Borders are quiet hairlines (`Rule`, or white at 10% over the camera), never a colored outline. Selection is the
  one exception: a selected swatch or art pick gets an accent ring.

## Typography

| Style | Size | Font | Use |
|---|---|---|---|
| displaySmall | 45 | Fraunces, SemiBold | The collection value on Home |
| headlineSmall | 22 | Fraunces, Medium | Screen titles |
| titleLarge | 20 | Fraunces, Medium | Section titles (Newest, Goals) |
| titleMedium | 16 | Lexend, Medium | Row titles |
| bodyMedium | 14 | Lexend | Body text |
| bodySmall | 12 | Lexend | Row subtitles |
| labelLarge/Medium/Small | 14/12/11 | Lexend, Medium, tabular | Counts, badges, small prices |
| PriceStyle | 15.5 | Lexend, Medium, tabular lining | Prices |

Sizes are in sp. Headings and prices are scaled by their font (Fraunces ×0.8, Lexend ×0.86) from the units in `Theme.kt`.

- Two fonts, fixed, no font picker. Rae picked Fraunces on 2026-11-05 after trying Cinzel ("Bolder A1"): it keeps
  the old-book feel in normal upper and lower case. Fraunces for headings and the big value; Lexend for everything
  else, prices included.
- Headings and body are different families, so a heading never looks like bold body text.
- Each heading level is a real step: 45 → 22 → 20 → 16.
- Weights are 400 and 500, with 600 only for the big figures (the collection value, life totals). Nothing is 700 or
  heavier: hierarchy comes from size and color, not weight. Fraunces ships in Medium and SemiBold only.
- Nothing under 11sp.
- Letter spacing is 0.1sp on labels and 0 on prices; headings are 0.
- No italic for emphasis. Flavor text and reminder text stay italic because that is how a printed card sets them.
- Line height is 1.2 for headings and 1.4 for body. Most body text is one-line list rows, so 1.4 rather than the
  1.6 used for web paragraphs.
- Numbers in lists use tabular figures so columns line up.
- Big text mode scales every style by 1.2.

## Spacing

An 8pt grid: `Space.s2` (8), `s4` (16), `s6` (24), `s8` (32), `s12` (48). The half steps `s1` (4) and `s3` (12) are
used only inside a component, for example icon to label or chip padding.

- Screen side margin: `Space.gutter` (16) on every screen.
- Between sections on a screen: `s6` (24); on Home `s8` (32), so each section reads on its own.
- Between rows in a group: 0, with a `Rule` line.
- Panel padding: `s4` (16).
- No literal dp in `padding`, `spacedBy`, `PaddingValues` or spacer heights, except 0 and 1 (hairlines) and
  layout sizes of 40dp or more. `DesignRulesTest` checks this on every build.

## Icons

- One set: Material Outlined (filled only for on-states, like a pinned or starred card).
- Two sizes, for a phone: 18dp next to text and 24dp for an icon on its own (icon buttons, the tab bar). The game
  table, which is read from across a table, uses bigger ones.
- Icons take a text color (`Palette.Icon`, `Muted`). A colored icon is a status mark (a gold star for a favorite, red
  for a deck problem), never decoration.
- No emoji in the interface. ✦ marks a foil in exported lists, like the symbol on a card.

## Copy

Short, concrete labels, as Rae wants: "Sort now", not "Learn more". No buzzwords (unlock, seamless, elevate,
leverage, empower, magic). A subtitle is one sentence at most. No emoji as icons.

- No exclamation marks.
- Labels, buttons, captions, toggle subtitles and one-sentence notes end without a period. Text of two or more
  sentences keeps its periods.
- A "…" after a menu item means it opens a picker; after a status ("Counting…") it means work is running.
- Short placeholders: "Search", not "Search for card…".
- A change says what it compares to: "+$41.80 (2.1%) since added".
- No greeting headlines. The title names the place: Home, Collection, a box's name.

## Shape and depth

- `Corners.Small` and `Corners.Medium` are both 4dp: near-square. Rae asked for no big rounded cards.
- Card images use `CardShape`, a real card's 5% corner.
- Panels are separated by a surface step, not by shadows. Only sheets and menus cast a small shadow.

## Components

Atoms are single pieces, molecules combine 2–4 atoms, and organisms are whole sections. Organisms never contain
other organisms. Every value comes from the tokens.

- **Atoms:** `GradientButton` (the one main button; flat accent fill despite its name), `QuietButton`, `ToolChip`,
  `Placeholders`, `RarityMark`, `ManaCost`, `OwnedBadge`, `Spinner`, `AutoSizeText`, icons (outlined, filled only
  for on-states).
- **Molecules:** row groups (`PlainGroup`), `EmptyState` (one line), `StatsStrip`, `ValueBar`, `UnderlineTabs`,
  `SwipeActions`, the search field.
- **Organisms:** `CollapsingHeader`, the bottom tab bar, `CardGrid`, `NewestStrip`, the Home value card,
  `LineChart`, sheets and dialogs.

## Hierarchy

- One main action per screen, filled with the accent. Every other action is quiet: text, an outline or an icon.
  Where a screen has states (scan result: add, count, remove, price check), each state has its own one.
  Game setup: Start is the main action; Resume for a saved game is an outline button.
- At most two buttons side by side.
- Text has three levels: `Text`, then `Muted`, then `Faint`. `Faint` is for disabled labels only: the readable
  levels are two, because a third readable grey would fall under 4.5:1 on the darker panels.
- Body text is left-aligned. Center only numbers in stat tiles and the empty-state line.

## Navigation

Five bottom tabs: Home, Game, Scan, Search, Collection. Scan is the raised accent circle in the middle, because
scanning is the core action. The tab bar hides on scroll down and comes back on scroll up.

## Deliberate choices

The [anti-slop design rules](https://github.com/febbhav/signs-of-ai-design/blob/main/design-rules.md) say the
"AI look" comes from many defaults at once with no decision behind any of them. These are Grimoire's decisions:

- **Palette:** ink brown and paper with one oxblood accent, taken from old binders and leather. Not purple, emerald or
  cream-by-default.
- **Type:** Fraunces, a soft old-style serif, for headings, like the title page of an old book; unslop names it as an
  editorial headline face. Lexend for text because it stays legible small. No Inter, Geist or Space Grotesk, and no font
  picker. Monospace (Plex Mono) only for the numbers strip in Collection, never body text.
- **Radius varies by element:** 4dp for panels, chips and buttons, a real card's 5% corner for card images, and a
  circle only for Scan.
- **Edges:** one treatment per element. Panels use a surface step; rows use a rule line. Never border plus shadow.
- **Glass:** only on the Scan screen, where controls float over a live camera. That's a real layering problem.
- **Motion:** used where it explains something: a scanned card flying to its pile, a row flashing when it changes,
  dice tumbling. One easing curve, `Motion.ease` (cubic-bezier 0.22, 1, 0.36, 1), and three lengths: `fast` 120ms
  for presses and toggles, `base` 200ms for menus and fades, `slow` 320ms for pages and things that enter. A press
  scales to 0.97 with no bounce. Dice keep their bounce because real dice bounce. The only loops are things that are
  live: loading placeholders and a foil's shimmer.
- **Case:** sentence case everywhere, headings included ("Sort now", "Back up", "Newest").
- **No invented content:** no sample counts, testimonials or fake data in the app. Empty states say what to do.

## Accessibility

- Text is 4.5:1 or better: `Text` and `Muted` on all three surfaces in both modes. `AccentText` is computed to 4.6:1.
- Touch targets are at least 48dp (`heightIn(min = 48.dp)` on buttons and rows).
- Every icon-only button has a content description. Mana costs and charts are read out in words.
- Screen pictures in CI are taken in dark, light and large text for every PR.

## Audit

Before changing UI, read this file. After changing it, check the PR's screen pictures against it.
`DesignRulesTest` fails the build on:
- off-grid spacing;
- a gradient accent;
- a color in `design-tokens.json` that the code doesn't use (the token file stays the source of truth);
- `Text` or `Muted` under 4.5:1 on any surface, in either mode;
- a weight over 600 outside the dice, label tape and mana symbols;
- text under 11sp;
- an exclamation mark, or a trailing period on a one-sentence `Text`;
- a colored shadow;
- `FastOutSlowInEasing` or a bouncy spring outside the dice.

### Where Grimoire departs from unslop

Each of these is a choice Rae made for this app. unslop's own rule is to keep a real brand, and these are it.

- **Corners stay 4dp.** unslop suggests 8–10 for controls and 12–16 for cards. Rae asked for no big rounded cards, and
  card images already carry a real card's corner.
- **Warm darks.** unslop wants app surfaces near-grey. Our dark ones are tinted toward ink brown, but at very low
  saturation (#100F0E, #1E1C19), close to unslop's own "warm" ramp, so no screen reads as brown. Light mode follows
  unslop: near-grey (#ECEBE8 under white panels) with only a trace of warmth. It was cream until Rae dropped it.
- **Charts are ink, not accent.** unslop gives the tracked line the accent. Rae chose less red (Bolder A), so the line
  is `Text` and the accent stays on actions.
- **A small chart under the Home value.** unslop keeps stat cards to numbers. The value card is Home's one moment,
  and its chart is the main chart there, with dates under it, not a decoration.
- **Game table and dice** keep their own colors, bold life totals at 600 and the dice bounce: they are objects on a
  real table, the app's other moment.
- **Label tape** prints in bold capitals with tracking because it imitates a label maker.

### What was not taken from the skills, and why

- **Fluid `clamp()` type and rem units:** these are for the web. Android uses sp, which already follows the phone's
  text size.
- **Local preview server and Tailwind or Flutter exports:** the app is Compose. The preview is the screen pictures
  CI draws on every PR, plus the "Grimoire redesign directions" canvas.
- **Cursor and Antigravity rule files:** nobody uses those tools here. `AGENTS.md` and `CLAUDE.md` load this file
  for coding agents.
- **Website-only checklist items** (hero, pricing, footer, testimonials, logo walls): the app has none of them.
- **unslop's landing-page layout, hero scenes and generated images:** the app has no landing page. Its moments come
  from card art Scryfall already provides.
- **unslop's story video:** a presentation piece; nobody asked for one.

| Date | Change |
|---|---|
| 2026-11-04 | Created. Spacing moved onto the 8pt grid in 111 files (1,782 values). Corners 3 → 4dp. `Faint` text level added. Gradient main button removed. Body line height 1.35 → 1.4. Press bounce removed. |
| 2026-11-05 | Fonts fixed to Cinzel + Lexend; font picker and six unused fonts removed; value 45sp, screen titles 22, section titles 20. |
| 2026-11-05 | Bolder A: Material primary is ink, so text buttons, switches and charts lose the red; charts are a thin ink line with a flat fill; Home sections 32 apart; `Motion` tokens for short animations. |
| 2026-11-05 | Headings Cinzel → Fraunces (Rae wasn't sure about Cinzel). Cinzel files removed, Fraunces Medium and SemiBold added. |
| 2026-11-05 | Screen-picture review: switches, checkboxes, radio buttons, focused fields, cursors, spinners, progress bars, the deck curve and stats bars moved from the accent to ink or `Muted`; the game setup line uses Lexend instead of shrunken Cinzel. |
| 2026-11-05 | unslop made the default philosophy. Direction written. Copy: 2 exclamation marks and 89 trailing periods removed, "Search" placeholder. Type: no 700 (Cinzel Medium added, Cinzel Bold removed), prices Medium, label tracking 0.1, nothing under 11sp. One easing curve; press 0.97 without a spring; card zoom settles without bounce. Scan's accent glow and the art color wash on box and deck pages removed. Icons 18/24. Six build checks added. |
| 2026-11-05 | Full pass over both rule sets. Always-dark and domain colors named; hard-coded dark colors replaced with `Stage`, `StagePanel`, `ArtBase`, `OnDarkSuccess` and `OnDarkDanger`. Four 36–40dp icon buttons raised to 48dp. Game setup has one main button. The scanned-card fan drops its border and keeps its shadow (one edge treatment). Em dashes removed from shared text. Token sync and contrast tests added. |
