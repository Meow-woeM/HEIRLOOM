# ATTRIBUTION.md — art & asset licenses

Every art pack shipped in the APK must be listed here with source URL and license.
Rules: CC0 preferred; attribution licenses (CC-BY) acceptable with the credit kept
below; **no NC (non-commercial) or ND (no-derivatives) licenses** — this is a paid-ad
commercial app and packs get palette-unified tint passes (derivatives).

## Currently shipped

| Asset | Source | License | Notes |
|---|---|---|---|
| Town Vista placeholder scenes | drawn in code (`VistaPlaceholder.kt`) | original | flat-color silhouettes, season-tinted sky |
| Launcher icon (covered wagon) | drawn in code (`ic_launcher_foreground.xml`) | original | |
| UI glyphs (⌂ ✦ ◌ …) | system fonts | — | no icon pack shipped |

## Recommended sources for the real vista art (drop into `app/src/main/assets/vista/`)

- Kenney.nl (CC0): https://kenney.nl/assets — "Pixel Platformer", "Tiny Town", "Pixel Vehicle Pack"
- Sprout Lands by Cup Nooble (itch.io, check license tier): https://cupnooble.itch.io/sprout-lands-asset-pack
- OpenGameArt CC0 collections: https://opengameart.org/ (filter license = CC0)

Expect style mixing across stages (no single free pack spans frontier → future city);
unify with a consistent palette overlay/tint pass per stage before export. Author scenes
at ~360×140 px and list them in `assets/vista/manifest.json` — no code changes needed.
