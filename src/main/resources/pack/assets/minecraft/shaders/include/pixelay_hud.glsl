// Pixelay: On-screen 2D HUD widget positioning engine.
// Decodes screen anchors and line offsets from color channels in 2D GUI space.

#ifndef PIXELAY_HUD_INCLUDED
#define PIXELAY_HUD_INCLUDED

const vec2 PIXELAY_HUD_CORNERS[4] = vec2[4](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));

// Returns true when this vertex belongs to a Pixelay on-screen HUD widget.
bool pixelay_is_hud(vec3 pos, mat4 proj, vec4 color) {
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    if (!isGui) {
        return false;
    }

    if (pos.y < -30.0 || pos.y > 65.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));

    // Tagged HUD widget main quad signature:
    // Red bits 2..3 == 1
    // Green bits 2..3 == 2 (text) or 3 (background)
    // Blue bits 2..3 == 1
    int gSig = (c.g >> 2) & 3;
    bool isSig = (((c.r >> 2) & 3) == 1) && (gSig == 2 || gSig == 3) && (((c.b >> 2) & 3) == 1);
    int anchor = (c.r & 3) | ((c.b & 3) << 2);

    return isSig && (anchor <= 8);
}

// Returns true when this vertex is a bossbar text shadow of a HUD element.
bool pixelay_is_hud_shadow(vec3 pos, mat4 proj, vec4 color) {
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    if (!isGui) {
        return false;
    }

    // Limit to the bossbar area
    if (pos.y < -30.0 || pos.y > 65.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));

    // Tagged HUD widget shadow quad signature after client divides color by 4:
    // Main quad had red bits 2..3 == 1, so shadow quad has red bits 0..1 == 1 and bits 2..3 == 0.
    // Green bits 0..1 == 2 (text) or 3 (background)
    // Blue bits 0..1 == 1
    int gShadow = c.g & 3;
    bool isShadowSig = (((c.r >> 2) & 3) == 0) && ((c.r & 3) == 1) && (gShadow == 2 || gShadow == 3) && ((c.b & 3) == 1);

    return isShadowSig;
}

// Calculates the screen position for a HUD widget vertex.
vec4 pixelay_hud_position(vec3 pos, mat4 proj, mat4 modelView, vec4 color, int vertexId) {
    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    ivec3 c = ivec3(round(color.rgb * 255.0));
    int anchor = (c.r & 3) | ((c.b & 3) << 2);
    int gSig = (c.g >> 2) & 3;
    bool isBg = (gSig == 3);

    float localX = pos.x - ui.x * 0.5;
    vec2 corner = PIXELAY_HUD_CORNERS[vertexId % 4];

    vec2 anchorPos = vec2(0.0);
    if (anchor == 0) { // TOP_LEFT
        anchorPos = vec2(0.0, 10.0);
    } else if (anchor == 1) { // TOP_CENTER (degree heading under compass)
        anchorPos = vec2(ui.x * 0.5, 34.0);
    } else if (anchor == 2) { // TOP_RIGHT
        anchorPos = vec2(ui.x, 10.0);
    } else if (anchor == 3) { // CENTER_LEFT
        anchorPos = vec2(0.0, ui.y * 0.5);
    } else if (anchor == 4) { // CENTER
        anchorPos = vec2(ui.x * 0.5, ui.y * 0.5);
    } else if (anchor == 5) { // CENTER_RIGHT
        anchorPos = vec2(ui.x, ui.y * 0.5);
    } else if (anchor == 6) { // BOTTOM_LEFT
        anchorPos = vec2(0.0, ui.y - 20.0);
    } else if (anchor == 7) { // BOTTOM_CENTER
        anchorPos = vec2(ui.x * 0.5, ui.y - 20.0);
    } else if (anchor == 8) { // BOTTOM_RIGHT
        anchorPos = vec2(ui.x, ui.y - 20.0);
    }

    float glyphY;
    float yOffset;

    if (isBg) {
        int bgHeight = ((c.g >> 4) & 15) | (((c.b >> 4) & 15) << 4);
        int yShift = ((c.r >> 4) & 15) | ((c.g & 3) << 4);
        float shift = float(yShift - 32);

        glyphY = corner.y * float(bgHeight);
        yOffset = shift;
    } else {
        int lineIndex = c.g & 3;
        float lineSpacing = 10.0;
        // Use standard 8.0px glyph height for text
        glyphY = corner.y * 8.0;

        if (anchor >= 6) {
            yOffset = -float(lineIndex) * lineSpacing;
        } else {
            yOffset = float(lineIndex) * lineSpacing;
        }
    }

    vec3 outPos = vec3(anchorPos.x + localX, anchorPos.y + yOffset + glyphY, pos.z);
    return proj * modelView * vec4(outPos, 1.0);
}

#endif
