# Grimoire Design System

Direction **A · Grimoire**, chosen by Rae on 2026-11-04. Built with the
[design-system skill](https://github.com/Jaywalker-not-a-whitewalker/DesignSystem) (tokens first, 8pt grid,
atoms → molecules → organisms), and checked against the
[AI-generated look checklist](https://aitoolpick.org/blog/ai-generated-website-checklist/).

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
| `Background` | #100F0E | #E7E2D5 | Screen |
| `Surface` | #1E1C19 | #FDFCF8 | Panels, sheets, dialogs |
| `SurfaceHigh` | #2B2824 | #DCD6C8 | Chips, inputs, pressed rows |
| `Text` | #ECE8E1 | #1C1B19 | Primary text |
| `Muted` | #A29C92 | #5E5A52 | Secondary text (4.6:1 or better) |
| `Faint` | #77716A | #8A8478 | Hints, timestamps, disabled. Never for anything you must read |
| `Rule` | #332F2A | #CAC4B5 | 1dp lines under rows and tabs |
| `Accent` | #9E3A33 | #8A2F29 | Oxblood. The user can change it |
| `AccentText` | computed | computed | Accent moved to 4.6:1 on the background, for text |
| `Success` / `Danger` / `Gold` / `Info` | | | Price up / down, foil and warnings, links in help |

Rules:
- The accent is for actions and the selected state only: the main button, the selected tab, links, Scan. It is
  never used for decoration, headings or borders.
- Gains and losses always show a sign (+/−) as well as a color, so they don't rely on red or green alone.
- No gradients on buttons or backgrounds. Gradients are allowed only as scrims, to keep text readable over card art
  or the camera.
- No colored side stripes on cards or rows.

## Typography

| Style | Size | Font | Use |
|---|---|---|---|
| displaySmall | 40 | Heading font | The collection value on Home |
| headlineSmall | 26 | Heading, SemiBold | Screen titles |
| titleLarge | 23 | Heading, SemiBold | Section titles (Newest, Goals) |
| titleMedium | 16 | Body, Medium | Row titles |
| bodyMedium | 14 | Body | Body text |
| bodySmall | 12 | Body | Row subtitles |
| labelLarge/Medium/Small | 14/12/11 | Body, Medium, tabular | Counts, badges, small prices |
| PriceStyle | 18 | Price font, tabular lining | Prices |

- Headings and body use different families (Cormorant SC over Lexend by default), so a heading never looks like
  bold body text.
- Each heading level is a real step: 40 → 26 → 23 → 16.
- Line height is 1.2 for headings and 1.4 for body. Most body text is one-line list rows, so 1.4 rather than the
  1.6 used for web paragraphs.
- Numbers in lists use tabular figures so columns line up.
- Big text mode scales every style by 1.2.

## Spacing

An 8pt grid: `Space.s2` (8), `s4` (16), `s6` (24), `s8` (32), `s12` (48). The half steps `s1` (4) and `s3` (12) are
used only inside a component, for example icon to label or chip padding.

- Screen side margin: `Space.gutter` (16) on every screen.
- Between sections on a screen: `s6` (24).
- Between rows in a group: 0, with a `Rule` line.
- Panel padding: `s4` (16).
- No literal dp in `padding`, `spacedBy`, `PaddingValues` or spacer heights, except 0 and 1 (hairlines) and
  layout sizes of 40dp or more. `DesignRulesTest` checks this on every build.

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
- At most two buttons side by side.
- Text has three levels: `Text`, then `Muted`, then `Faint`.
- Body text is left-aligned. Center only numbers in stat tiles and the empty-state line.

## Navigation

Five bottom tabs: Home, Game, Scan, Search, Collection. Scan is the raised accent circle in the middle, because
scanning is the core action. The tab bar hides on scroll down and comes back on scroll up.

## Copy

Short, concrete labels, as Rae wants: "Sort now", not "Learn more". No buzzwords (unlock, seamless, elevate,
leverage, empower). A subtitle is one sentence at most. No emoji as icons.

## Deliberate choices

The [anti-slop design rules](https://github.com/febbhav/signs-of-ai-design/blob/main/design-rules.md) say the
"AI look" comes from many defaults at once with no decision behind any of them. These are Grimoire's decisions:

- **Palette:** ink brown and paper with one oxblood accent, taken from old binders and leather. Not purple, emerald or
  cream-by-default.
- **Type:** a small-caps serif (Cormorant SC) for headings over Lexend for body. Inter and Space Grotesk are user
  options, not the default. Monospace is for prices and counts only, never body text.
- **Radius varies by element:** 4dp for panels, chips and buttons, a real card's 5% corner for card images, and a
  circle only for Scan.
- **Edges:** one treatment per element. Panels use a surface step; rows use a rule line. Never border plus shadow.
- **Glass:** only on the Scan screen, where controls float over a live camera. That's a real layering problem.
- **Motion:** used where it explains something: a scanned card flying to its pile, a row flashing when it changes,
  dice tumbling. Press feedback has no bounce. Dice keep their bounce because real dice bounce.
- **Case:** sentence case for buttons and labels ("Sort now", "Back up"). Small caps belong to the heading font only.
- **No invented content:** no sample counts, testimonials or fake data in the app. Empty states say what to do.

## Accessibility

- Text is 4.5:1 or better: `Text` and `Muted` on all three surfaces in both modes. `AccentText` is computed to 4.6:1.
- Touch targets are at least 48dp (`heightIn(min = 48.dp)` on buttons and rows).
- Every icon-only button has a content description. Mana costs and charts are read out in words.
- Screen pictures in CI are taken in dark, light and large text for every PR.

## Audit

Before changing UI, read this file. After changing it, check the PR's screen pictures against it.
`DesignRulesTest` fails the build on off-grid spacing and gradient buttons.

| Date | Change |
|---|---|
| 2026-11-04 | Created. Spacing moved onto the 8pt grid in 111 files (1,782 values). Corners 3 → 4dp. `Faint` text level added. Gradient main button removed. Body line height 1.35 → 1.4. Press bounce removed. |
