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
