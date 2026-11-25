# Font-Based Positioning

This page explains how to position text and icons at exact $(X, Y)$ coordinates without shaders.

## Java API: `HudPosition`

Pixelay provides the `dev.horuse.pixelay.hud.HudPosition` utility class.

### 1. Position a Component at $(X, Y)$

Use `HudPosition.at(component, x, y)`:

```java
import dev.horuse.pixelay.hud.HudPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

// Positions a label 20 pixels to the right, 14 pixels down
Component label = Component.text("Player Level: 42", NamedTextColor.GREEN);
Component positioned = HudPosition.at(label, 20, 14);
```

The method executes three actions:
1. Adds `HudSpace.offset(x)` before the text to move the client cursor.
2. Applies `.font(HudPosition.font(y))` to shift the text baseline vertically.
3. Adds `HudSpace.offset(-x - visualWidth)` after the text to return the cursor to the origin.

### 2. Manual Font Selection

If you manage horizontal spacing manually, select the font key directly:

```java
Component text = Component.text("Warning!")
    .font(HudPosition.font(50)); // Applies pixelay:y_50
```

### 3. Measuring Component Width

Use `HudPosition.visualWidth(component)` to get the exact width in font pixels:

```java
Component badge = Component.text("⚡ VIP");
int widthPx = HudPosition.visualWidth(badge);
```

### 4. Registering HUD Heights (`YFontRegistry`)

When `hud.font-y-offsets.mode` is set to `registered` (default), Pixelay builds only the `(anchor, y)` font files registered before `ServerLoadEvent`. Register all required offsets in your plugin `onEnable()`:

```java
import dev.horuse.pixelay.hud.HudAnchor;
import dev.horuse.pixelay.hud.YFontRegistry;

// Register a single (anchor, y) pair
YFontRegistry.register(HudAnchor.TOP_LEFT, 9);

// Register consecutive 10px lines starting at baseY (for multi-line widgets)
YFontRegistry.registerLines(HudAnchor.TOP_RIGHT, 9, 2); // registers y=9 and y=19
```

Calling `HudPosition.font(...)` at runtime also registers the pair automatically, so running `/pixelay reload` rebuilds the pack with any newly used offsets.

## Configuration

Control the generated Y-offset range and languages in `plugins/Pixelay/config.yml`:

```yaml
hud:
  font-y-offsets:
    # Enable automatic generation of HUD font files in the resource pack
    enabled: true

    # Generation mode: registered | range | auto
    mode: registered

    # Minimum vertical offset in pixels (used in range/auto modes)
    min-y: -20

    # Maximum vertical offset in pixels (positive moves down)
    max-y: 220

    # Pixel increment between generated files (1 gives single-pixel precision)
    step: 1

    # Languages and character sets to include:
    # - en: Standard ASCII Latin characters and numbers
    # - uk: Ukrainian Cyrillic characters (А-Я, а-я, Ґ, ґ, Є, є, І, і, Ї, ї)
    # - symbols: UI symbols, arrows, currency signs (₴, €, $, ₽)
    languages:
      - en
      - uk
      - symbols

    # Include custom glyphs scanned from plugins/Pixelay/resourcepacks/
    include-scanned-glyphs: true
```

## Language Character Sets

Pixelay references vanilla Minecraft client textures:

| Identifier | Source Texture | Included Characters |
| :--- | :--- | :--- |
| `en` | `minecraft:font/ascii.png` | Standard Latin letters (`A-Z`, `a-z`), numbers (`0-9`), and basic punctuation (`!@#$%^&*()_+-=`). |
| `uk` | `minecraft:font/nonlatin_european.png`<br>`minecraft:font/accented.png` | Complete Cyrillic alphabet (`А-Я`, `а-я`), Ukrainian special letters (`Ґ`, `ґ`, `Є`, `є`, `І`, `і`, `Ї`, `ї`), typographic quotes (`«»`, `“”`, `„`), apostrophes (`'`). |
| `symbols` | `minecraft:font/nonlatin_european.png` | Currency symbols (`₴`, `€`, `$`, `₽`), arrows (`←`, `↑`, `→`, `↓`, `⇄`), bullet points (`•`), UI marks (`✔`, `❌`, `⭐`). |

## Automatic Custom Glyph Scanning and On-Demand Fonts

Pixelay scans all resource packs inside `plugins/Pixelay/resourcepacks/` during startup and reload, and includes only `font/default.json` glyphs in generated HUD fonts. When plugins register their coordinates via `YFontRegistry` (or call `HudPosition.font`), Pixelay builds `pixelay-pack.zip` on `ServerLoadEvent` with only the registered `(anchor, y)` font files.

When `include-scanned-glyphs: true` is active:
1. `PackFontScanner` discovers custom characters in external packs (for example, `\uE001` mapped to `custom_pack:textures/icons/ping.png`).
2. `YFontGenerator` duplicates each custom glyph provider across all generated `y_<offset>.json` files.
3. The custom icons become available at every vertical offset on the screen.
