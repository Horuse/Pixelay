# Depth Layering & Z-Index

Pixelay manages layer depths automatically to prevent visual flickering between overlapping elements.

## Stacking Order

Elements render in tree order:
1. Parent containers render before their child elements.
2. Earlier sibling elements render before later sibling elements.
3. Elements with higher `.zIndex(int)` render in front of elements with lower `.zIndex`.

## Controlling Depth with `zIndex`

Use `.zIndex(int)` when you want an element to float above sibling content:

```java
// Main content card
Box<?> card = column(title, content)
    .padding(20)
    .background(0xFF1E293B);

// Floating badge placed in front
Box<?> badge = text(viewer, 14, "<gold>★ FEATURED</gold>")
    .position(Position.ABSOLUTE)
    .top(12)
    .right(12)
    .zIndex(10); // Renders above card content
```

## When to Use `zIndex`

- **Dropdown Menus**: Ensure menu lists appear above cards below them.
- **Modal Dialogs**: Place dialog windows in front of page content.
- **Floating Badges**: Position status chips in the corner of cards.
- **Tooltips**: Display descriptive popups above hovered items.
