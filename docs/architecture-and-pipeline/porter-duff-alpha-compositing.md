# Colors & Translucency Guidelines

Pixelay supports full 32-bit ARGB colors with semi-transparent backgrounds and borders.

## Setting Colors

You can specify colors with packed integer literals or CSS color strings:

```java
// Packed ARGB hex int (0xAARRGGBB)
box.background(0xFF0F172A); // Opaque dark blue
box.background(0x8038BDF8); // 50% translucent sky blue

// CSS hex color string
box.background("#0F172A");     // Opaque hex
box.background("#38BDF880");   // 8-digit hex with alpha
box.background("rgba(56, 189, 248, 0.5)"); // CSS rgba syntax
```

## Translucency Best Practice

In Minecraft, rendering translucent text displays over the open game world can cause depth artifacts.

Pixelay blends translucent colors automatically. Follow this simple rule for clean visual results:

> [!TIP]
> Always give your root container an opaque background color (such as `0xFF0F172A` or `0xFF1E293B`).
> When the root background is opaque, Pixelay bakes all child translucent cards, hover highlights, and scrollbars into solid composite colors. This prevents world see-through holes.
