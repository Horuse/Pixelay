# Player Leaderboard

The `Leaderboard` example renders high score rankings with alternating row colors and trophy icons.

## Layout Structure

- **Header Row**: Rank, Player Name, Level, Total Score.
- **Top 3 Highlight**: Gold (`#F59E0B`), Silver (`#94A3B8`), and Bronze (`#D97706`) rank badges.
- **Alternating Rows**: Uses alternating translucent backgrounds (`0x20FFFFFF` and `0x10FFFFFF`) for easy readability.
- **Player Heads**: Uses custom font characters or 3D player skull items.

## Row Code Pattern

```java
Box<?> createLeaderboardRow(Player viewer, int rank, String name, int score) {
    String rankColor = switch (rank) {
        case 1 -> "<gold>#1 🏆</gold>";
        case 2 -> "<gray>#2 🥈</gray>";
        case 3 -> "<#CD7F32>#3 🥉</#CD7F32>";
        default -> "<dark_gray>#" + rank + "</dark_gray>";
    };

    return row(
        text(viewer, 20, rankColor).width(60),
        text(viewer, 20, "<b>" + name + "</b>").grow(1),
        text(viewer, 20, "<yellow>" + String.format("%,d", score) + " pts")
    )
        .align(Align.CENTER)
        .padding(10, 16)
        .background(rank % 2 == 0 ? 0x15FFFFFF : 0x0AFFFFFF);
}
```
