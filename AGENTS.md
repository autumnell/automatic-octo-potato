# Agents

Before writing or changing any UI code, read `design/Design.md` and use the tokens it names (`Palette`, `Space`,
`Motion`, `Corners`, the typography in `ui/theme/Theme.kt`). Values live in `design/design-tokens.json`. Don't hardcode
colors, spacing or animation curves.

The default design philosophy is unslop (`design/unslop/SKILL.md`, with its sign list in
`design/unslop/references/signs.md`). Follow it for anything `Design.md` doesn't settle; where `Design.md` departs from
it, `Design.md` wins. Check the PR's screen pictures against both.
