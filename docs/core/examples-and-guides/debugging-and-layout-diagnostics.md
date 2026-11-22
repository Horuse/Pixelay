# Debugging & Layout Diagnostics

Pixelay includes built-in visual inspection tools to help developers debug layouts and inspect bounding boxes.

## The Debug Command

Run the layout inspection command on your server:

```text
/pixelay debug layout
```

This command toggles visual debug overlays for your active player session.

## Visual Inspection Features

- **Bounding Box Outlines**: Draws colored bounding lines around every `Box`, `Row`, `Column`, and `Grid` element.
- **Padding & Margin Overlays**: Displays green inner padding zones and orange outer margin zones.
- **Overflow Warnings**: Highlights components whose measured children exceed container boundaries in bright red.
- **Alignment Markers**: Visualizes flex cross-axis and main-axis alignment guides.
