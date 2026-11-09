# Scrolling & Stack Containers

Pixelay provides specialized containers for overlapping content and scrollable viewports.

## Stack Container (`Stack`)

A `Stack` overlays all child nodes directly on top of each other. It is created with `stack(children...)`:

```java
Box<?> layeredCard = stack(
    box().size(400, 300).background(0xFF1E293B), // Background quad
    imageOrModel,                                 // Middle layer
    column(titleText, descText).padding(20)       // Foreground overlay
);
```

## Scroll View (`ScrollView`)

A `ScrollView` provides a clipped viewport with interactive vertical and horizontal scrolling:

```java
Box<?> scrollArea = scrollView(longListContent)
    .size(600, 400)
    .scrollbarWidth(10)
    .scrollbarTrackColor(0x30FFFFFF)
    .scrollbarThumbColor(0xFF38BDF8)
    .smoothScroll(true);
```

### Key ScrollView Features

- **Clipping**: Automatically enforces `overflow(Overflow.HIDDEN)` on the viewport.
- **Smooth Inertia**: Interpolates scroll offsets over time for smooth mouse wheel action.
- **Draggable Scrollbar**: Players can grab and drag the scrollbar thumb with spectator cursors.
- **Wheel Routing**: Scroll events forward automatically to the hovered scroll view.
