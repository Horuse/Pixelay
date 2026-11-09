# Rendering Modes & Performance

Pixelay uses Minecraft display entities (`TextDisplay`, `ItemDisplay`, `BlockDisplay`) to render UI elements.

## Choosing a Render Mode

You configure the rendering mode in `plugins/Pixelay/config.yml`:

```yaml
render:
  mode: PACKET # Options: PACKET or SERVER
```

### 1. Packet Mode (`PACKET`) — Recommended

- Creates virtual fake entities via PacketEvents.
- Adds zero Bukkit entities to the server world.
- Chunks never save interface entities to disk.
- Each player can see completely personalized content (player profile, personal balance, quest tracker).

### 2. Server Entity Mode (`SERVER`)

- Spawns real Bukkit display entities in the server world.
- All players in the world see the exact same shared display.
- Useful for public server spawn scoreboards and shared information walls.

## Smooth In-Place Updates

Pixelay updates entity metadata in place:
- Color, scale, and position updates send a single metadata packet.
- Entities are never destroyed and respawned during animations or hover events.
- All frame updates send as a single packet bundle to prevent screen tearing.
