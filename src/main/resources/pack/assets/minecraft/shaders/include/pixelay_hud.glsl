// Pixelay: On-screen 2D HUD positioning and smooth compass engine.
// Decodes screen anchors and line offsets from color channels in 2D GUI space.
// Supports GPU sub-tick yaw interpolation for 144+ FPS compass tape scrolling.

#ifndef PIXELAY_HUD_INCLUDED
#define PIXELAY_HUD_INCLUDED

// Marker tag in low color bits (bit 8 = 256).
#define PIXELAY_HUD_TAG 256
// Dedicated compass tape marker in low blue bits (value 5).
#define PIXELAY_HUD_COMPASS_TAG 5

// Returns true when this vertex belongs to a Pixelay on-screen HUD element.
bool pixelay_is_hud(vec3 pos, mat4 proj, vec4 color) {
    // Only in 2D orthographic GUI projection where HUD elements render.
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    if (!isGui) {
        return false;
    }
    // Vanilla BossHealthOverlay renders title text between y = 1.0 and y = 18.0.
    if (pos.y < 1.0 || pos.y > 18.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));
    int code = (c.r & 7) | ((c.g & 7) << 3) | ((c.b & 7) << 6);
    bool isTaggedWidget = (code & PIXELAY_HUD_TAG) != 0;
    bool isCompassTape = (c.b & 7) == PIXELAY_HUD_COMPASS_TAG;
    return isTaggedWidget || isCompassTape;
}

// Calculates the screen position for a HUD vertex.
vec4 pixelay_hud_position(vec3 pos, mat4 proj, mat4 modelView, vec4 color) {
    // Exact screen size in GUI pixels from the orthographic projection matrix.
    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    ivec3 c = ivec3(round(color.rgb * 255.0));
    bool isCompassTape = (c.b & 7) == PIXELAY_HUD_COMPASS_TAG;

    int anchor = 0;
    int lineIndex = 0;
    if (!isCompassTape) {
        int code = (c.r & 7) | ((c.g & 7) << 3) | ((c.b & 7) << 6);
        anchor = code & 15;
        lineIndex = (code >> 4) & 15;
    } else {
        anchor = 9; // COMPASS_TAPE
    }

    // BossBar title text starts centered at ui.x * 0.5.
    // When elements cancel their line width with negative space, pos.x starts at 0.5 * ui.x.
    float localX = pos.x - ui.x * 0.5;
    float glyphY = pos.y - 3.0; // Baseline offset in vanilla boss bar

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
        anchorPos = vec2(10.0, ui.y - 18.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 7) { // BOTTOM_CENTER
        anchorPos = vec2(ui.x * 0.5, ui.y - 18.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 8) { // BOTTOM_RIGHT
        anchorPos = vec2(ui.x - 10.0, ui.y - 18.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 9) { // COMPASS_TAPE
        anchorPos = vec2(ui.x * 0.5, 10.0);
        yOffset = float(lineIndex) * lineSpacing;
    }

    // Anchor 9 Compass tape with sub-tick GPU interpolation:
    if (isCompassTape) {
        // Red channel encodes target yaw (0..360), Green channel encodes previous yaw (0..360).
        float targetYaw = (float(c.r) / 255.0) * 360.0;
        float prevYaw = (float(c.g) / 255.0) * 360.0;
        // Fractional tick between 0.0 and 1.0 at native monitor refresh rate.
        float t = fract(GameTime * 24000.0);
        float diff = targetYaw - prevYaw;
        if (diff > 180.0) diff -= 360.0;
        if (diff < -180.0) diff += 360.0;
        float smoothYaw = prevYaw + diff * t;
        // Shift tape horizontally (1.0 pixel per degree).
        localX -= smoothYaw * 1.0;
    }

    vec3 outPos = vec3(anchorPos.x + localX, anchorPos.y + yOffset + glyphY, pos.z);

    // Clip tape strip inside the 183px frame boundaries.
    if (isCompassTape) {
        float frameCenter = ui.x * 0.5;
        if (outPos.x < (frameCenter - 89.0) || outPos.x > (frameCenter + 89.0)) {
            return vec4(2.0, 2.0, 2.0, 1.0);
        }
    }

    return proj * modelView * vec4(outPos, 1.0);
}

#endif
