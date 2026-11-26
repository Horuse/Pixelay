# Pseudo-States & Styles

Pixelay supports state-dependent styling similar to CSS pseudo-classes (`:hover`, `:active`, `:disabled`).

## Standard Pseudo-States

The `State` enum defines built-in interactive states:

- `State.HOVERED`: Triggered when the player spectator cursor points inside the component bounding box.
- `State.SELECTED`: Triggered when an item or tab is active.
- `State.DISABLED`: Disables click interactions and applies subdued styling.

```java
import dev.horuse.pixelay.layout.State;

Box<?> card = row(title, icon)
    .padding(16)
    .background(0xFF1E293B)
    .borderColor(0xFF334155)
    // Style applied when cursor hovers over card
    .state(State.HOVERED, s -> s
        .background(0xFF334155)
        .borderColor(0xFF38BDF8)
        .scale(1.05)
    )
    // Style applied when item is disabled
    .state(State.DISABLED, s -> s
        .opacity(0.5)
    );
```

## Custom States

You can register custom state names and toggle them dynamically at runtime:

```java
// Define styling for custom "unread" state
card.state("unread", s -> s.borderColor(0xFFEF4444));

// Toggle state based on server events
card.toggle("unread", hasUnreadMessages);
```
