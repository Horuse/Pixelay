# Layout Lifecycle & Refreshing

Pixelay calculates sizes and positions automatically using a two-pass layout cycle.

## How Layout Calculation Works

1. **Measurement**: Children calculate their natural width and height based on text content, padding, and borders.
2. **Arrangement**: Parents place children into final pixel rectangles based on flexbox rules, grid tracks, or alignment settings.

## Updating Layouts Dynamically

When your data changes (for example, a player gains coins, changes equipment, or clicks a button), update your layout:

### 1. Automatic Dynamic Text

Use lambda suppliers for text that changes regularly:

```java
// Re-evaluates automatically on every refresh
text(viewer, 18, () -> "Coins: " + economy.getBalance(player));
```

### 2. Triggering a Layout Refresh

Call `mountedLayout.refresh()` to recalculate bounds and update display entities:

```java
// After changing children, sizes, or state
mountedLayout.refresh();
```

Pixelay recalculates the layout in memory and sends only the changed entity properties to viewers.

### 3. Moving Layouts with 3D GPU Interpolation

Use `teleport(...)` to move a mounted layout across the world without entity flicker:

```java
Location targetLocation = player.getLocation().add(3, 1.5, 4);
float targetYaw = 225.0f;

Canvas targetCanvas = currentCanvas.withLocation(targetLocation, targetYaw);

// Smoothly moves the interface to the new position over 20 game ticks (1.0 second)
mountedLayout.teleport(targetCanvas, 20);

// Or teleport instantly without transition
mountedLayout.teleport(targetCanvas);
```

When you call `teleport(...)`:
- Pixelay keeps existing display entities active.
- The server sends position and transformation duration metadata.
- The Minecraft client graphics card interpolates the full movement at native monitor refresh rates (144+ FPS).
- Click hitboxes update to the target canvas plane automatically.
