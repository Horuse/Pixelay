# Screen HUD Architecture

This document describes the design principles and rendering pipeline of the Pixelay Screen HUD system.

## The Positioning Challenge

Minecraft displays BossBar title text at a fixed position in the top center of the screen.
To build a HUD with widgets across the entire screen, an interface system must move text to arbitrary $(X, Y)$ coordinates.

Two technical approaches exist:

1. **Core Shader Vertex Manipulation**:
   - Encodes coordinates into character colors or vertex positions.
   - Core vertex shaders decode the metadata and translate vertices in Normalized Device Coordinates (NDC).
   - **Problems**: Divides or modifies color values. Text drop shadows (which divide RGB by 4) trigger false signature matches, leading to stretched text or distorted colors.

2. **Font-Based Ascent and Space Advances (Pixelay HUD)**:
   - Sets character vertical position through font provider `ascent` values in `assets/pixelay/font/y_<offset>.json`.
   - Sets character horizontal position through space glyph advances in `assets/pixelay/font/space.json`.
   - **Benefits**: Client renders standard text natively. Zero core shaders required for HUD text. Colors, drop shadows, and gradients stay 100% accurate.

## Vertical Shift via Font Ascent

In Minecraft bitmap font providers, the baseline vertical coordinate shifts according to the `ascent` property:

$$\text{Screen Y} = \text{Default Line Y} + (\text{Base Ascent} - \text{Provider Ascent})$$

To move a character down by $Y$ pixels:

$$\text{Provider Ascent} = \text{Base Ascent} - Y$$

Pixelay generates font definitions for each vertical pixel offset:

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "minecraft:font/ascii.png",
      "ascent": -8,
      "height": 8,
      "chars": [ ... ]
    }
  ]
}
```

When a component uses `.font(Key.key("pixelay", "y_15"))`, Minecraft shifts the entire text string downwards by 15 pixels.

## Horizontal Shift via Space Advances

Pixelay includes font provider `pixelay:space`.
It defines positive and negative advances for private Unicode characters:

- `\uE201` to `\uE240`: negative shifts from -1 to -64 pixels.
- `\uE250` to `\uE259`: large negative steps (80, 100, 128, 160, 200, 256, 320, 400, 512, 1024 px).
- `\uE301` to `\uE340`: positive shifts from +1 to +64 pixels.

The helper method `HudSpace.offset(pixels)` composes the shortest glyph sequence for any horizontal distance.

## Comparison with Other Plugins

| Feature | BetterHud | Pixelay Screen HUD |
| :--- | :--- | :--- |
| **Font Generation Method** | Rasterizes TTF files into 400+ PNG images | References vanilla Minecraft textures directly |
| **Resource Pack Size** | ~4.2 MB | ~25 KB to 350 KB |
| **Texture Count** | 490+ image files | 0 extra image files |
| **Color Fidelity** | Modifies RGB bits in shaders | Pure RGB / Hex / Adventure gradients |
| **Drop Shadows** | Distorted by color bit masks | Pure vanilla shadow quads |
| **Custom Glyph Support** | Requires manual configuration | Automatically scans external resource packs |
| **Cyrillic Support** | Large extra texture sheets | Native `nonlatin_european.png` and `accented.png` |
