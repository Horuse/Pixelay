# CSS Transitions & Easing Curves

When a component changes state or dimensions, Pixelay smoothly interpolates properties over time.

## Setting a Transition

Use `.transition(durationMs, easing)`:

```java
import dev.horuse.pixelay.layout.Easing;

button.background(0xFF2563EB)
    .state(State.HOVERED, s -> s.background(0xFF1D4ED8))
    .transition(200, Easing.EASE_OUT);
```

## Supported Easing Functions

Pixelay includes standard CSS easing curves:

- `Easing.LINEAR`: Constant speed from start to end.
- `Easing.EASE`: Gentle acceleration at start, strong deceleration at end.
- `Easing.EASE_IN`: Starts slowly and accelerates.
- `Easing.EASE_OUT`: Starts quickly and decelerates smoothly.
- `Easing.EASE_IN_OUT`: Accelerates halfway and decelerates toward the end.
- `Easing.cubicBezier(x1, y1, x2, y2)`: Custom cubic bezier curve.

## Interpolated Properties

The transition system smoothly animates:
- Background and border ARGB colors (including alpha).
- 2D translation offsets (`translateX`, `translateY`).
- Scale factors (`scaleX`, `scaleY`).
- Rotation angles.
- Opacity values.
