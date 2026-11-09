# CSS Grid System

Pixelay includes a 2D CSS Grid container created with `grid(children...)`. It supports pixel tracks, fractional tracks, repeating definitions, and cell spans.

## Grid Track Definitions

Use track helper methods to define grid rows and columns:

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.layout.*;

Box<?> productGrid = grid(itemCards)
    .columns(repeat(3, fr(1))) // 3 equal fractional columns
    .rows(repeat(2, px(240)))   // 2 fixed 240px rows
    .gap(16)
    .padding(20)
    .background(0xFF0F172A);
```

## Track Measurement Units

- **`px(double)`**: Fixed size track in layout pixels.
- **`fr(double)`**: Fractional track unit. Takes a share of remaining free space after fixed tracks resolve.
- **`auto()`**: Sizes the track according to the largest child element in that track.
- **`repeat(int count, Track... tracks)`**: Duplicates track patterns across the grid.

## Cell Spanning & Sparse Placement

Children can span multiple rows and columns:

```java
Box<?> bannerCard = createBanner()
    .columnSpan(3) // Spans across all 3 grid columns
    .rowSpan(1);

Box<?> sidePanel = createSidePanel()
    .rowSpan(2);   // Spans vertically across 2 rows
```

Pixelay uses sparse placement to automatically advance items into the next available row when cell collisions occur.
