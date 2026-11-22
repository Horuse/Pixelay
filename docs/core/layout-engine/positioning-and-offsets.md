# Positioning & Offsets

Pixelay supports standard CSS positioning schemes via `.position(Position)`:

```java
import dev.horuse.pixelay.layout.Position;
```

## Positioning Modes

- **`Position.STATIC`** *(default)*: Normal document flow. Coordinates respect parent flex or grid layout.
- **`Position.RELATIVE`**: Retains normal space in the parent flow, then applies visual pixel offsets (`top`, `left`, `bottom`, `right`).
- **`Position.ABSOLUTE`**: Removes the element from normal layout flow. Anchors the box relative to the nearest positioned ancestor (or the canvas plane).

## Anchoring & Insets

Positioned elements use pixel edge anchors:

```java
// Floating badge pinned to the bottom-right corner of a parent card
Box<?> badge = text(viewer, 14, "<gold>★ NEW</gold>")
    .position(Position.ABSOLUTE)
    .right(16)
    .bottom(16)
    .zIndex(10);
```

| Method | Description |
| :--- | :--- |
| `.top(px)` / `.bottom(px)` | Distance from the top or bottom edge of the parent bounding box. |
| `.left(px)` / `.right(px)` | Distance from the left or right edge of the parent bounding box. |
| `.inset(all)` | Sets equal distance on all four edges. |
| `.zIndex(int)` | Controls stacking order in depth calculations. Higher values render in front. |
