# Layout and Anchors

This guide explains how to arrange HUD components into structured screen layouts.

## The Screen Anchor System

The `dev.horuse.pixelay.hud.HudAnchor` enumeration defines nine anchor positions on the display:

```
+-------------------------------------------------------------+
| TOP_LEFT                 TOP_CENTER               TOP_RIGHT |
|                                                             |
| CENTER_LEFT                CENTER              CENTER_RIGHT |
|                                                             |
| BOTTOM_LEFT            BOTTOM_CENTER           BOTTOM_RIGHT |
+-------------------------------------------------------------+
```

### Alignment Characteristics

- **Left Anchors** (`TOP_LEFT`, `CENTER_LEFT`, `BOTTOM_LEFT`):
  Text starts at the anchor coordinate and extends to the right.
- **Center Anchors** (`TOP_CENTER`, `CENTER`, `BOTTOM_CENTER`):
  Text centers horizontally on the anchor coordinate.
- **Right Anchors** (`TOP_RIGHT`, `CENTER_RIGHT`, `BOTTOM_RIGHT`):
  Text ends at the anchor coordinate and extends to the left.

### Hotbar Anchors

Three anchors follow the vanilla hotbar (182 x 22 GUI pixels at the bottom center of the screen):

```
                 ABOVE_HOTBAR
                      v
HOTBAR_LEFT > [ hotbar 182 x 22 ] < HOTBAR_RIGHT
```

- `HOTBAR_LEFT`: left end of the hotbar. Content ends at the anchor and extends to the left.
- `HOTBAR_RIGHT`: right end of the hotbar. Content starts at the anchor and extends to the right.
- `ABOVE_HOTBAR`: center of the hotbar top edge. Content is centered and grows upward.

All three are bottom anchors, so `margin` moves the box away from the hotbar edge and up from the screen bottom.
Use `CENTER` for elements around the crosshair: it is the exact screen center.

## Layout Containers

Use `dev.horuse.pixelay.layout.Layout` to compose text lines and icons:

```java
import dev.horuse.pixelay.hud.HudAnchor;
import dev.horuse.pixelay.hud.LayoutHudElement;
import dev.horuse.pixelay.layout.Layout;
import dev.horuse.pixelay.layout.LayoutItem;

// 1. Define individual rows
LayoutItem title = Layout.text("<gold><bold>STARMINE</bold></gold>");
LayoutItem status = Layout.row(
    Layout.text("<gray>Ping: <green>38ms</green></gray>"),
    Layout.text("<gray>TPS: <green>20.0</green></gray>")
).gap(8);

// 2. Combine rows into a column
LayoutItem container = Layout.column(title, status).gap(3);

// 3. Attach to a Screen HUD element at TOP_LEFT with 10px X offset and 14px Y offset
LayoutHudElement widget = new LayoutHudElement(HudAnchor.TOP_LEFT, 10, 14, container);
```

## Offsets from Screen Anchors

Pass pixel offsets to `LayoutHudElement` or `BasicHudElement` to set margins from screen edges:

```java
// Top-left element: 10 pixels from the left, 14 pixels down from the top
LayoutHudElement topLeft = new LayoutHudElement(HudAnchor.TOP_LEFT, 10, 14, container);

// Top-right element: 10 pixels left from the right edge, 14 pixels down
LayoutHudElement topRight = new LayoutHudElement(HudAnchor.TOP_RIGHT, -10, 14, pingWidget);

// Bottom-right element: 10 pixels left from the right edge, 16 pixels up
LayoutHudElement bottomRight = new LayoutHudElement(HudAnchor.BOTTOM_RIGHT, -10, -16, itemWidget);
```

## Zero-Advance Composites

When combining multiple widgets into a single BossBar name, each widget must return the text cursor to its starting point.

Pixelay automates this in `HudPosition.at(component, x, y, width)`:

```
[Move cursor to X] -> [Render glyphs with Y font] -> [Move cursor back by (-X - width)]
```

Because total horizontal advance equals zero, widgets do not interfere with each other or drift across the screen.
