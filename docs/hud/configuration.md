# Configuration Reference

This page documents the configuration options for the Pixelay Screen HUD module in `plugins/Pixelay/config.yml`.

## Screen HUD Configuration

Configure the font generator and positioning ranges under the `hud.font-y-offsets` section:

```yaml
hud:
  font-y-offsets:
    # Set to true to generate font files in pixelay-pack.zip
    enabled: true

    # Generation mode:
    # - registered: build only (anchor, y) pairs registered via YFontRegistry (default)
    # - range: always build the full min-y..max-y range for all anchors
    # - auto: use registered pairs when present, otherwise fall back to min-y..max-y
    mode: registered

    # Lowest vertical offset in pixels (used in range/auto modes)
    min-y: -20

    # Highest vertical offset in pixels (positive values shift downward)
    # Range: 50 to 500. Default: 220
    max-y: 220

    # Step in pixels between consecutive generated font files
    # Set to 1 for single-pixel precision, or 2 to halve file count
    # Default: 1
    step: 1

    # Languages and character sheets to include in font providers:
    # - en: Standard Latin alphabet, digits, punctuation (minecraft:font/ascii.png)
    # - uk: Ukrainian Cyrillic alphabet, Ї, Ґ, є, і (nonlatin_european.png, accented.png)
    # - symbols: UI symbols, arrows, currency signs (nonlatin_european.png)
    languages:
      - en
      - uk
      - symbols

    # When true, scans external resource packs in plugins/Pixelay/resourcepacks/
    # and includes custom glyphs from font/default.json in generated HUD fonts
    # Default: true
    include-scanned-glyphs: true
```

> **Note on on-demand generation**: Pixelay builds `pixelay-pack.zip` on `ServerLoadEvent`. When plugins register offsets via `YFontRegistry` (or `HudPosition.font`), Pixelay generates only those exact `(anchor, y)` font files. The `min-y`, `max-y`, and `step` values act as a fallback range when no offsets are registered.

## Options Specification

### `hud.font-y-offsets.enabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enables generation of font files for vertical positioning in `pixelay-pack.zip`. When false, Pixelay does not write `y_<offset>.json` files.

### `hud.font-y-offsets.min-y`
- **Type**: Integer
- **Default**: `-20`
- **Description**: The minimum vertical offset in screen pixels. Negative numbers shift text upward from the baseline.

### `hud.font-y-offsets.max-y`
- **Type**: Integer
- **Default**: `220`
- **Description**: The maximum vertical offset in screen pixels. Positive numbers shift text downward from the baseline.

### `hud.font-y-offsets.step`
- **Type**: Integer
- **Default**: `1`
- **Description**: Pixel increment between generated font files. A value of `1` generates files for every single pixel. A value of `2` generates files for even pixels only.

### `hud.font-y-offsets.languages`
- **Type**: List of strings
- **Default**: `[en, uk, symbols]`
- **Description**: Specifies character sets to include:
  - `en`: Standard ASCII letters, numbers, and basic punctuation.
  - `uk`: Ukrainian Cyrillic characters, including uppercase, lowercase, and special characters (`Ґ`, `ґ`, `Є`, `є`, `І`, `і`, `Ї`, `ї`).
  - `symbols`: Common mathematical, navigation, currency, and UI symbols.

### `hud.font-y-offsets.include-scanned-glyphs`
- **Type**: Boolean
- **Default**: `true`
- **Description**: When true, `PackFontScanner` reads custom glyphs from `font/default.json` in external resource packs under `plugins/Pixelay/resourcepacks/`. Non-default fonts (such as compasses or widget backgrounds) are skipped so they are not duplicated into HUD Y-offset fonts.
