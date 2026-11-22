# Configuration & Resource Packs

Pixelay provides automated font measurement, glyph extraction, and shader cursor assets.

## The Configuration File (`config.yml`)

The configuration file resides in `plugins/Pixelay/config.yml`. It contains settings for rendering, font loading, and cursor tracking:

```yaml
render:
  # Mode: PACKET (packet-based fake entities) or SERVER (Bukkit entities)
  mode: PACKET
  default-billboard: FIXED
  text-see-through: false

fonts:
  # Automatically inspect resource packs in plugins/Pixelay/resourcepacks/
  scan-packs: true
  cache-glyph-widths: true

screen-session:
  # Default screen camera distance in blocks
  camera-distance: 2.5
  # Enable core shader cursor support
  enable-shader-cursor: true
```

## Resource Pack Font Scanning

Pixelay accurately measures text widths at runtime. It includes a complete vanilla font width table generated from Minecraft glyph data.

When your server uses custom font icons or unicode textures:
1. Place the `.zip` resource pack file in `plugins/Pixelay/resourcepacks/`.
2. Pixelay scans `assets/minecraft/font/*.json` on server startup.
3. Pixelay measures glyph bitmaps and stores exact pixel widths.
4. Text wrapping and alignment automatically support custom glyphs.

## Shader Cursor Resource Pack

Pixelay includes a client resource pack for zero-latency GPU cursors:
- Uses the `rendertype_text` core shader.
- Renders cursor sprites on the GPU at uncapped monitor refresh rates.
- Eliminates server network latency from cursor movements.
