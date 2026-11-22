# Buttons & Click Listeners

The `Button` component provides clickable interface elements with hover styles and debounce protection.

## Basic Usage

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.layout.Button;
import dev.horuse.pixelay.layout.State;
import dev.horuse.pixelay.layout.Easing;

Button actionButton = button(viewer, 20, "<white><b>Confirm Action</b>", player -> {
    player.sendMessage("Action confirmed!");
})
    .padding(10, 20)
    .background(0xFF2563EB)
    .state(State.HOVERED, s -> s.background(0xFF1D4ED8))
    .transition(150, Easing.EASE_OUT);
```

## Click Debounce & Safety

- Buttons automatically include a default click debounce time ($100\text{ ms}$).
- Rapid click packets from clients cannot trigger duplicate handler executions.
- Buttons set default pointer cursor (`"pointer"`), which triggers the hand cursor in shader mode.
