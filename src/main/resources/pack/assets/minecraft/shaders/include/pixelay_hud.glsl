// Pixelay: On-screen 2D HUD positioning and smooth compass engine.
// Decodes screen anchors and line offsets from color channels in 2D GUI space.
// Supports GPU sub-tick yaw interpolation for 144+ FPS compass tape scrolling.

#ifndef PIXELAY_HUD_INCLUDED
#define PIXELAY_HUD_INCLUDED

// Corner vertex UV mapping for glyph quads: (0,0), (0,1), (1,1), (1,0).
const vec2 PIXELAY_HUD_CORNERS[4] = vec2[4](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));

// Returns true when this vertex belongs to a Pixelay on-screen HUD element.
bool pixelay_is_hud(vec3 pos, mat4 proj, vec4 color) {
    // Only in 2D orthographic GUI projection.
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    if (!isGui) {
        return false;
    }

    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    // Strictly limit to the BossBar area (top of screen, centered):
    // pos.y in [0.0, 85.0] and within 170px of screen center.
    // Excludes scoreboard, chat, inventory, action bar, hotbar.
    if (pos.y < 0.0 || pos.y > 85.0 || abs(pos.x - ui.x * 0.5) > 170.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));

    // Compass tape strip: exactly blue 253
    bool isCompassTape = (c.b == 253);

    // Tagged HUD widget: blue ends in 6, green has bit 2 set, and anchor <= 9
    int anchor = c.r & 15;
    bool isTaggedWidget = ((c.b & 7) == 6) && ((c.g & 4) == 4) && (anchor <= 9);

    return isCompassTape || isTaggedWidget;
}

// Returns true when this vertex is a bossbar text shadow of a HUD element.
bool pixelay_is_hud_shadow(vec3 pos, mat4 proj, vec4 color) {
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    if (!isGui) {
        return false;
    }

    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    // Must be in the BossBar area where the shadow is drawn:
    if (pos.y < 0.0 || pos.y > 85.0 || abs(pos.x - ui.x * 0.5) > 170.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));

    // Compass tape shadow: blue 63 ((253 & 0xFC) >> 2 == 63)
    bool isCompassTapeShadow = (c.b == 63);

    // HUD widget shadow: (c.b & 1) == 1 && (c.g & 1) == 1
    // (survived from bit 2 of blue and bit 2 of green after >> 2)
    bool isWidgetShadow = ((c.b & 1) == 1) && ((c.g & 1) == 1);

    return isCompassTapeShadow || isWidgetShadow;
}

// Calculates the screen position for a HUD vertex.
vec4 pixelay_hud_position(vec3 pos, mat4 proj, mat4 modelView, vec4 color, int vertexId) {
    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    ivec3 c = ivec3(round(color.rgb * 255.0));
    bool isCompassTape = (c.b == 253);

    int anchor = 0;
    int lineIndex = 0;
    if (!isCompassTape) {
        anchor = c.r & 15;
        lineIndex = c.g & 3;
    } else {
        anchor = 9; // COMPASS_TAPE
    }

    // BossBar title text starts centered at ui.x * 0.5.
    // When elements cancel their line width with negative space, pos.x starts at 0.5 * ui.x.
    float localX = pos.x - ui.x * 0.5;

    // Use quad corner index to compute vertical offset independent of boss bar slot:
    vec2 corner = PIXELAY_HUD_CORNERS[vertexId % 4];
    float glyphHeight = (isCompassTape || anchor == 9) ? 21.0 : 9.0;
    float glyphY = corner.y * glyphHeight;

    vec2 anchorPos = vec2(0.0);
    float lineSpacing = 10.0;
    float yOffset = float(lineIndex) * lineSpacing;

    if (anchor == 0) { // TOP_LEFT
        anchorPos = vec2(10.0, 10.0);
    } else if (anchor == 1) { // TOP_CENTER
        anchorPos = vec2(ui.x * 0.5, 10.0);
    } else if (anchor == 2) { // TOP_RIGHT
        anchorPos = vec2(ui.x - 10.0, 10.0);
    } else if (anchor == 3) { // CENTER_LEFT
        anchorPos = vec2(10.0, ui.y * 0.5);
    } else if (anchor == 4) { // CENTER
        anchorPos = vec2(ui.x * 0.5, ui.y * 0.5);
    } else if (anchor == 5) { // CENTER_RIGHT
        anchorPos = vec2(ui.x - 10.0, ui.y * 0.5);
    } else if (anchor == 6) { // BOTTOM_LEFT
        anchorPos = vec2(10.0, ui.y - 20.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 7) { // BOTTOM_CENTER
        anchorPos = vec2(ui.x * 0.5, ui.y - 20.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 8) { // BOTTOM_RIGHT
        anchorPos = vec2(ui.x - 10.0, ui.y - 20.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 9) { // COMPASS_TAPE
        anchorPos = vec2(ui.x * 0.5, 10.0);
        yOffset = float(lineIndex) * lineSpacing;
    }

    // Anchor 9 Compass tape with sub-tick GPU interpolation:
    if (isCompassTape) {
        float targetYaw = (float(c.r) / 255.0) * 360.0;
        float prevYaw = (float(c.g) / 255.0) * 360.0;
        float t = fract(GameTime * 24000.0);
        float diff = targetYaw - prevYaw;
        if (diff > 180.0) diff -= 360.0;
        if (diff < -180.0) diff += 360.0;
        float smoothYaw = prevYaw + diff * t;
        localX -= smoothYaw * 1.0;
    }

    vec3 outPos = vec3(anchorPos.x + localX, anchorPos.y + yOffset + glyphY, pos.z);

    // Clip tape strip inside the 183px frame boundaries:
    if (isCompassTape) {
        float frameCenter = ui.x * 0.5;
        if (outPos.x < (frameCenter - 89.0) || outPos.x > (frameCenter + 89.0)) {
            return vec4(2.0, 2.0, 2.0, 1.0);
        }
    }

    return proj * modelView * vec4(outPos, 1.0);
}

#endif
