# Input & Interaction Controls

When a `ScreenSession` is active, Pixelay intercepts and routes player input packets to ensure smooth interaction.

## Spectator Click Interception

Minecraft spectator mode does not send standard block break or interact packets.
Pixelay intercepts spectator attack and interact packets via PacketEvents:
- Left-click packets map to primary button clicks and canvas drawing actions.
- Right-click packets map to secondary actions and context triggers.

## Sneak Blocking & Chat Suppression

To prevent accidental interface dismissal and unwanted player movement:
- **Sneak Action**: Intercepts sneak packets to safely trigger screen closure or custom cancel callbacks.
- **Chat Suppression**: Suppresses open chat overlays and prevents keyboard interference while the interactive screen is active.
