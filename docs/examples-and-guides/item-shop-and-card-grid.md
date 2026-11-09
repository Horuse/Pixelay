# Item Shop & Card Grid

The `ItemShop` example builds an in-game market with 3D item displays and responsive grids.

## Key Architecture Pattern

1. **Category Navigation Tabs**:
   - Filter bar with buttons: `All Equipment`, `Weapons`, `Armor`, `Artifacts`.
   - Active tab highlights with active background and bold text.
2. **Responsive CSS Grid**:
   - `grid(itemCards).columns(repeat(4, fr(1))).gap(16)`.
   - Evenly divides horizontal space across 4 columns.
3. **Item Card Component**:
   - Renders 3D Bukkit `ItemStack` using `item(stack)`.
   - Rarity badge (`LEGENDARY`, `EPIC`, `RARE`) styled with custom colors.
   - Price row with coin emoji and purchase button.

## Card Code Snippet

```java
Box<?> card = column(
    badge(viewer, item.rarity(), item.rarityBg(), item.rarityColor()),
    space(8),
    item(new ItemStack(item.material()))
        .size(64, 64)
        .alignSelf(Align.CENTER),
    space(8),
    text(viewer, 20, "<b>" + item.name() + "</b>"),
    spacer(),
    row(
        text(viewer, 18, "<gold>🪙 " + item.price()),
        spacer(),
        button(viewer, 16, "Buy", p -> buyItem(p, item))
            .padding(6, 14)
            .background(0xFF10B981)
    ).align(Align.CENTER)
)
    .padding(14)
    .background(0xFF1E293B)
    .state(State.HOVERED, s -> s.scale(1.03).borderColor(0xFF38BDF8))
    .transition(150, Easing.EASE_OUT);
```
