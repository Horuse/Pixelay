# JSON Document Schema

Pixelay defines a canonical JSON schema that maps 1:1 to the component tree.

## Schema Structure

```json
{
  "version": 1,
  "resolution": {
    "width": 1920,
    "height": 1080
  },
  "root": {
    "tagName": "column",
    "id": "main-container",
    "style": {
      "padding": { "top": 24, "right": 24, "bottom": 24, "left": 24 },
      "background": "#0F172A",
      "align": "CENTER",
      "gap": 16
    },
    "children": [
      {
        "tagName": "text",
        "fontSize": 28,
        "content": "<gradient:#38BDF8:#818CF8><b>Dashboard Title</b></gradient>"
      },
      {
        "tagName": "button",
        "fontSize": 20,
        "content": "<white><b>Confirm</b>",
        "style": {
          "padding": { "vertical": 10, "horizontal": 20 },
          "background": "#2563EB",
          "cursor": "pointer"
        },
        "states": {
          "hovered": {
            "background": "#1D4ED8",
            "scale": 1.05
          }
        },
        "transition": {
          "durationMs": 150,
          "easing": "ease-out"
        }
      }
    ]
  }
}
```

External tools and web editors can serialize layout trees to this schema and reload them dynamically on the server.
