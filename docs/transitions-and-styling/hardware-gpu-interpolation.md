# Smooth Hardware Animations

Pixelay delivers fluid UI animations by using client-side GPU interpolation on Minecraft display entities.

## Adding Smooth Animations

Apply `.transition(...)` to any box:

```java
import dev.horuse.pixelay.layout.State;
import dev.horuse.pixelay.layout.Easing;

Box<?> card = row(icon, title)
    .padding(16)
    .background(0xFF1E293B)
    .border(2, 0xFF334155)
    // Style applied on hover
    .state(State.HOVERED, s -> s
        .scale(1.05)
        .translateY(-4)
        .borderColor(0xFF38BDF8)
    )
    // Smooth 200 ms transition with ease-out curve
    .transition(200, Easing.EASE_OUT);
```

## Animated Properties

The transition system automatically animates:
- **Scale**: `.scale(factor)` or `.scaleX(f)` / `.scaleY(f)`.
- **Translation**: `.translateX(px)` and `.translateY(px)`.
- **Background & Border Colors**: Smooth color fading including alpha.
- **Opacity**: `.opacity(0.0 .. 1.0)`.

## Uncapped Client Frame Rates

In vanilla Minecraft, the server ticks only 20 times per second.
Pixelay tells the client graphics card to interpolate transitions on every render frame.
Players on 144 Hz, 240 Hz, or 360 Hz monitors see completely smooth movement with zero server lag.
