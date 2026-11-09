# Interactive Game Menu

The `MenuScreen` example creates a modern server hub and player dashboard.

## Key Architecture Pattern

1. **Left Sidebar (Profile & Statistics)**:
   - Displays player name, rank badge (`MVP+`), and region.
   - Includes a `ProgressBar` showing account XP ($82\%$).
   - Shows currency balances (Coins, Gems, Tokens) with spaced rows.
2. **Right Area (Game Realms)**:
   - Displays interactive cards for game modes (Survival, SkyBlock, Dungeons).
   - Each card uses `.state(State.HOVERED, ...)` and `.transition(150, Easing.EASE_OUT)` for scale and border highlighting.
   - Click handlers trigger server connections or arena queues.

## Code Example

```java
public static Box<?> buildBox(Player viewer) {
    Box<?> profileCard = column(
        row(
            badge(viewer, "MVP+", 0x25E0AF68, "#E0AF68"),
            space(8),
            text(viewer, 24, "<b>" + viewer.getName() + "</b>")
        ).align(Align.CENTER),
        space(12),
        progressBar(0.82)
            .width(360)
            .trackHeight(10)
            .fillColor(0xFF38BDF8)
            .trackColor(0x30FFFFFF)
    )
        .padding(16)
        .background(0xFF1E293B);

    return row(profileCard, spacer(), realmCards)
        .padding(24)
        .background(0xFF0F172A);
}
```
