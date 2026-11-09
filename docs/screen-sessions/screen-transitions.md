# Screen Transitions

Pixelay includes cinematic transition effects for screen entry, screen switching, and game world reveals.

## Playing Transitions

Use the `Transitions` utility class to trigger transitions:

```java
import dev.horuse.pixelay.screen.Transitions;
import dev.horuse.pixelay.screen.ScreenTransition;

// Smooth black fade-in (600 ms)
Transitions.fadeIn(player, 600);

// Smooth black fade-out (600 ms)
Transitions.fadeOut(player, 600);

// Linear directional screen wipe (400 ms)
Transitions.play(player, ScreenTransition.linearWipe(400));
```

## Available Transition Styles

- **Fade**: Full-screen opacity interpolation.
- **Linear Wipe**: Sweeps horizontally or vertically across the screen (`WIPE_RIGHT`, `WIPE_LEFT`, `WIPE_DOWN`, `WIPE_UP`).
- **Diagonal Corner Wipe**: Sweeps outward from a corner (`CORNER_WIPE_TOP_LEFT`, `CORNER_WIPE_BOTTOM_RIGHT`).
- **Curtain Wipe**: Opens outward from the center like a theater curtain.
