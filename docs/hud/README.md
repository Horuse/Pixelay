# Pixelay Screen HUD

Pixelay Screen HUD provides a lightweight on-screen interface engine for Minecraft Paper servers.
It renders custom widgets, player status indicators, coordinate displays, and notifications directly on the screen.

## Key Features

- **Font-Based Positioning**: Positions text components at exact $(X, Y)$ screen coordinates with font vertical ascents and space glyph advances.
- **Pure Color Fidelity**: Avoids color bit-packing in GLSL vertex shaders. All text colors, gradients, and shadows render without distortion.
- **Multi-Language Support**: Supports English (`en`), Ukrainian Cyrillic (`uk`), and standard UI symbols (`symbols`).
- **Automatic Glyph Integration**: Scans external resource packs and makes custom icons and glyphs available across all vertical offsets.
- **Layout Engine**: Organizes multiple text lines and icons with column and row containers, gaps, and alignments.
- **Zero-Advance Composites**: Returns the text cursor to the origin after each component to prevent drift.

## How It Operates

Pixelay sends HUD components through an invisible BossBar layer with the Adventure text API.
The client renders the text characters on the screen without visible BossBar frames:

```
+-------------------------------------------------------------+
| [Player | ⚡ 210]                  [322° NW]      [Ping: 38ms] |  <-- Screen Top
|                                                             |
|                                                             |
|                                                             |
|                                            [Item | 30/90]   |  <-- Screen Bottom
+-------------------------------------------------------------+
```

## Quick Navigation

- [Architecture](architecture.md): Learn how font ascents and space glyphs position elements.
- [Font-Based Positioning](font-positioning.md): Use the `HudPosition` API and configure language character sets.
- [Layout and Anchors](layout-system.md): Align elements to screen anchors and compose layouts.
- [Configuration](configuration.md): Read the full configuration specification.
- [Examples and Guides](examples.md): See complete examples for status widgets and coordinate overlays.
