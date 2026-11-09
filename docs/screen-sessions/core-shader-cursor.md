# Core Shader Cursor

Standard server-side cursors suffer from network ping delay: every mouse movement must travel to the server and back before the cursor moves on screen.
Pixelay resolves this issue with its client-side Core Shader Cursor.

## How the Shader Cursor Works

When the Pixelay resource pack is active:
1. Pixelay installs a custom vertex and fragment shader into Minecraft's `rendertype_text` pipeline.
2. The client graphics card calculates cursor screen coordinates from view angles locally.
3. The cursor moves at the full refresh rate of the monitor (144 Hz, 240 Hz, or higher).
4. Visual cursor latency is zero milliseconds.

## CSS Cursor Types

Pixelay dynamically updates the cursor icon based on the CSS `.cursor(...)` property of the hovered box:

```java
button.cursor("pointer");       // Displays hand pointer icon
textArea.cursor("text");        // Displays I-beam text selection icon
canvas.cursor("crosshair");     // Displays crosshair brush icon
disabled.cursor("not-allowed"); // Displays prohibition icon
```
