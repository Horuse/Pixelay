// Pixelay: On-screen 2D HUD positioning and smooth compass engine.
// Decodes 9 screen anchors from the vertex ascent offset and positions glyphs in 2D GUI space.
// Supports GPU sub-tick yaw interpolation for 144+ FPS compass tape scrolling.

#ifndef PIXELAY_HUD_INCLUDED
#define PIXELAY_HUD_INCLUDED

// Magic base offset for HUD glyphs. Normal text has ascent < 1000.
#define PIXELAY_HUD_MAGIC 5000000.0
#define PIXELAY_HUD_ANCHOR_STEP 100000.0

// Anchor indices:
// 0: TOP_LEFT      (0, 0)
// 1: TOP_CENTER    (width / 2, 0)
// 2: TOP_RIGHT     (width, 0)
// 3: CENTER_LEFT   (0, height / 2)
// 4: CENTER        (width / 2, height / 2)
// 5: CENTER_RIGHT  (width, height / 2)
// 6: BOTTOM_LEFT   (0, height)
// 7: BOTTOM_CENTER (width / 2, height)
// 8: BOTTOM_RIGHT  (width, height)
// 9: COMPASS_TAPE  (width / 2, 0 with sub-tick yaw interpolation)

// Returns true when this vertex belongs to a Pixelay on-screen HUD element.
bool pixelay_is_hud(vec3 pos, mat4 proj) {
    // Only in 2D orthographic GUI projection where HUD elements render.
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    return isGui && pos.y >= 4000000.0;
}

// Calculates the screen position for a HUD vertex.
vec4 pixelay_hud_position(vec3 pos, mat4 proj, mat4 modelView, vec4 color) {
    // Exact screen size in GUI pixels from the orthographic projection matrix.
    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    float hudY = pos.y - PIXELAY_HUD_MAGIC;
    int anchor = int(floor(hudY / PIXELAY_HUD_ANCHOR_STEP + 0.5));
    float localY = hudY - float(anchor) * PIXELAY_HUD_ANCHOR_STEP;
    // BossBar title text is centered at ui.x * 0.5 by vanilla.
    float localX = pos.x - ui.x * 0.5;

    vec2 anchorPos = vec2(0.0);
    if (anchor == 0) {
        anchorPos = vec2(0.0, 0.0);
    } else if (anchor == 1 || anchor == 9) {
        anchorPos = vec2(ui.x * 0.5, 0.0);
    } else if (anchor == 2) {
        anchorPos = vec2(ui.x, 0.0);
    } else if (anchor == 3) {
        anchorPos = vec2(0.0, ui.y * 0.5);
    } else if (anchor == 4) {
        anchorPos = vec2(ui.x * 0.5, ui.y * 0.5);
    } else if (anchor == 5) {
        anchorPos = vec2(ui.x, ui.y * 0.5);
    } else if (anchor == 6) {
        anchorPos = vec2(0.0, ui.y);
    } else if (anchor == 7) {
        anchorPos = vec2(ui.x * 0.5, ui.y);
    } else if (anchor == 8) {
        anchorPos = vec2(ui.x, ui.y);
    }

    // Anchor 9: Compass tape with sub-tick GPU interpolation.
    if (anchor == 9) {
        // Red channel encodes target yaw (0..360), Green channel encodes previous yaw (0..360).
        float targetYaw = color.r * 360.0;
        float prevYaw = color.g * 360.0;
        // Fractional tick between 0.0 and 1.0 at native monitor refresh rate.
        float t = fract(GameTime * 24000.0);
        float diff = targetYaw - prevYaw;
        if (diff > 180.0) diff -= 360.0;
        if (diff < -180.0) diff += 360.0;
        float smoothYaw = prevYaw + diff * t;
        // Shift tape horizontally (2.0 pixels per degree).
        localX -= smoothYaw * 2.0;
    }

    // Baseline in vanilla boss bar is approximately 12.0. Subtract it so offset 0 aligns to top.
    vec3 outPos = vec3(anchorPos.x + localX, anchorPos.y + (localY - 12.0), pos.z);
    return proj * modelView * vec4(outPos, 1.0);
}

#endif
