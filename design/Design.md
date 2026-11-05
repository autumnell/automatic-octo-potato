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
| `Faint` | #77716A | #8A8478 | Disabled labels only. It is under 4.5:1, so never for text you must read |
| `Rule` | #332F2A | #CAC4B5 | 1dp lines under rows and tabs |
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

## Typography

| Style | Size | Font | Use |
|---|---|---|---|
| displaySmall | 45 | Cinzel, Bold | The collection value on Home |
| headlineSmall | 22 | Cinzel, SemiBold | Screen titles |
| titleLarge | 20 | Cinzel, SemiBold | Section titles (Newest, Goals) |
| titleMedium | 16 | Lexend, Medium | Row titles |
| bodyMedium | 14 | Lexend | Body text |
| bodySmall | 12 | Lexend | Row subtitles |
| labelLarge/Medium/Small | 14/12/11 | Lexend, Medium, tabular | Counts, badges, small prices |
| PriceStyle | 15.5 | Lexend, Bold, tabular lining | Prices |

Sizes are in sp. Headings and prices are scaled by their font (Cinzel ×0.8, Lexend ×0.86) from the units in `Theme.kt`.

- Two fonts, fixed (Rae, 2026-11-05: direction "Bolder A1 · Engraved", no font picker). Cinzel's engraved
  capitals for headings and the big value; Lexend for everything else, prices included.
- Headings and body are different families, so a heading never looks like bold body text.
- Each heading level is a real step: 45 → 22 → 20 → 16.
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

## Copy

Short, concrete labels, as Rae wants: "Sort now", not "Learn more". No buzzwords (unlock, seamless, elevate,
leverage, empower). A subtitle is one sentence at most. No emoji as icons.

## Deliberate choices

The [anti-slop design rules](https://github.com/febbhav/signs-of-ai-design/blob/main/design-rules.md) say the
"AI look" comes from many defaults at once with no decision behind any of them. These are Grimoire's decisions:

- **Palette:** ink brown and paper with one oxblood accent, taken from old binders and leather. Not purple, emerald or
  cream-by-default.
- **Type:** Cinzel, a capitals-only serif cut from Roman inscriptions, for headings, like a title tooled into a
  binder's spine. Lexend for text because it stays legible small. No Inter, Geist or Space Grotesk, and no font
  picker. Monospace (Plex Mono) only for the numbers strip in Collection, never body text.
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
`DesignRulesTest` fails the build on:
- off-grid spacing;
- a gradient accent;
- a color in `design-tokens.json` that the code doesn't use (the token file stays the source of truth);
- `Text` or `Muted` under 4.5:1 on any surface, in either mode.

### What was not taken from the skill, and why

- **Fluid `clamp()` type and rem units:** these are for the web. Android uses sp, which already follows the phone's
  text size.
- **Local preview server and Tailwind or Flutter exports:** the app is Compose. The preview is the screen pictures
  CI draws on every PR, plus the "Grimoire redesign directions" canvas.
- **Cursor and Antigravity rule files:** nobody uses those tools here. `AGENTS.md` and `CLAUDE.md` load this file
  for coding agents.
- **Website-only checklist items** (hero, pricing, footer, testimonials, logo walls): the app has none of them.

| Date | Change |
|---|---|
| 2026-11-04 | Created. Spacing moved onto the 8pt grid in 111 files (1,782 values). Corners 3 → 4dp. `Faint` text level added. Gradient main button removed. Body line height 1.35 → 1.4. Press bounce removed. |
| 2026-11-05 | Fonts fixed to Cinzel + Lexend; font picker and six unused fonts removed; value 45sp, screen titles 22, section titles 20. |
| 2026-11-05 | Bolder A: Material primary is ink, so text buttons, switches and charts lose the red; charts are a thin ink line with a flat fill; Home sections 32 apart; `Motion` tokens for short animations. |
| 2026-11-05 | Full pass over both rule sets. Always-dark and domain colors named; hard-coded dark colors replaced with `Stage`, `StagePanel`, `ArtBase`, `OnDarkSuccess` and `OnDarkDanger`. Four 36–40dp icon buttons raised to 48dp. Game setup has one main button. The scanned-card fan drops its border and keeps its shadow (one edge treatment). Em dashes removed from shared text. Token sync and contrast tests added. |
