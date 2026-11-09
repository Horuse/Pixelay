# Typography & Text Metrics

The `Text` component renders Adventure MiniMessage text strings using pixel-accurate font glyph tables.

## Basic Usage

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.layout.Text;

// Static formatted text
Text title = text(viewer, 28, "<gradient:#38BDF8:#818CF8><b>Dashboard</b></gradient>");

// Dynamic supplier text (re-evaluated on every refresh tick)
Text clock = text(viewer, 18, () -> "<gray>Server Time: <white>" + System.currentTimeMillis());
```

## Alignment & Wrapping Rules

| Method | Description |
| :--- | :--- |
| `.fontSize(px)` | Sets target text height in canvas layout pixels. |
| `.textAlign(HAlign)` | Sets horizontal alignment (`LEFT`, `CENTER`, `RIGHT`) within the box. |
| `.whiteSpace(WhiteSpace)` | `NORMAL` (automatic word wrap) or `NOWRAP` (single line). |
| `.wordBreak(WordBreak)` | Controls wrap points (`NORMAL`, `BREAK_WORD`, `BREAK_ALL`). |
| `.textOverflow(TextOverflow)` | `CLIP` or `ELLIPSIS`. `ELLIPSIS` appends `"..."` when single-line text exceeds width. |

## Pixel Font Measurement

Pixelay measures each character width before rendering:
- Supports all standard Minecraft vanilla glyph widths and spacing.
- Automatically calculates bold glyph expansion ($+1\text{ px}$).
- Measures custom resource pack font glyphs from bitmap textures.
