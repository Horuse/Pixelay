# 2D Drawing Canvas

The `layout.Canvas` component embeds a pixel art drawing surface directly into any layout box.

## Programmatic Pixel Buffer

You can read and write ARGB color pixels directly:

```java
import dev.horuse.pixelay.layout.Canvas;

// Create a 32x32 pixel drawing surface
Canvas drawPad = new Canvas(viewer, 32, 32)
    .size(320, 320)
    .background(0xFF1E293B);

// Draw programmatic pixels
drawPad.setPixel(0, 0, 0xFFFF0000); // Red pixel at top-left
drawPad.fillRect(4, 4, 16, 16, 0xFF38BDF8); // Blue rectangle
```

## Interactive Player Painting

Enable drawing mode so players can paint onto the canvas with spectator cursors:

```java
drawPad.drawable(true)
    .brushColor(0xFF38BDF8)
    .brushSize(1)
    .onPixelPaint((player, x, y, color) -> {
        // Handle pixel paint event
    });
```

## 2D Greedy Meshing Optimization

Displaying individual Minecraft entities for every pixel is inefficient.
Pixelay combines adjacent pixels with identical colors into large rectangles using a 2D greedy meshing algorithm.
A 32x32 canvas with solid shapes renders using only a few `TextDisplay` background panels instead of 1024 entities.
