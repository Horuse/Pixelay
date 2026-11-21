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

    if (pos.y < 0.0 || pos.y > 65.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));

    // Tagged HUD widget main quad signature:
    // Red bits 2..3 == 1
    // Green bits 2..3 == 2
    // Blue bits 2..3 == 1
    bool isSig = (((c.r >> 2) & 3) == 1) && (((c.g >> 2) & 3) == 2) && (((c.b >> 2) & 3) == 1);
    int anchor = (c.r & 3) | ((c.b & 3) << 2);

    return isSig && (anchor <= 8);
}

// Returns true when this vertex is a bossbar text shadow of a HUD element.
bool pixelay_is_hud_shadow(vec3 pos, mat4 proj, vec4 color) {
    bool isGui = proj[2][3] == 0.0 && proj[3][3] == 1.0;
    if (!isGui) {
        return false;
    }

    // Limit strictly to the bossbar title area
    if (pos.y < 0.0 || pos.y > 35.0) {
        return false;
    }

    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));
    if (abs(pos.x - ui.x * 0.5) > 60.0) {
        return false;
    }

    ivec3 c = ivec3(round(color.rgb * 255.0));

    // Tagged HUD widget shadow quad signature after client divides by 4:
    // Red bits 0..1 == 1
    // Green bits 0..1 == 2
    // Blue bits 0..1 == 1
    bool isShadowSig = ((c.r & 3) == 1) && ((c.g & 3) == 2) && ((c.b & 3) == 1);

    return isShadowSig;
}

// Calculates the screen position for a HUD widget vertex.
vec4 pixelay_hud_position(vec3 pos, mat4 proj, mat4 modelView, vec4 color, int vertexId) {
    vec2 ui = ceil(2.0 / vec2(proj[0][0], -proj[1][1]));

    ivec3 c = ivec3(round(color.rgb * 255.0));
    int anchor = (c.r & 3) | ((c.b & 3) << 2);
    int lineIndex = c.g & 3;

    float localX = pos.x - ui.x * 0.5;

    vec2 corner = PIXELAY_HUD_CORNERS[vertexId % 4];
    float glyphY = corner.y * 9.0;

    vec2 anchorPos = vec2(0.0);
    float lineSpacing = 10.0;
    float yOffset = float(lineIndex) * lineSpacing;

    if (anchor == 0) { // TOP_LEFT
        anchorPos = vec2(0.0, 10.0);
    } else if (anchor == 1) { // TOP_CENTER
        anchorPos = vec2(ui.x * 0.5, 28.0);
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
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 7) { // BOTTOM_CENTER
        anchorPos = vec2(ui.x * 0.5, ui.y - 20.0);
        yOffset = -float(lineIndex) * lineSpacing;
    } else if (anchor == 8) { // BOTTOM_RIGHT
        anchorPos = vec2(ui.x, ui.y - 20.0);
        yOffset = -float(lineIndex) * lineSpacing;
    }

    vec3 outPos = vec3(anchorPos.x + localX, anchorPos.y + yOffset + glyphY, pos.z);
    return proj * modelView * vec4(outPos, 1.0);
}

#endif
