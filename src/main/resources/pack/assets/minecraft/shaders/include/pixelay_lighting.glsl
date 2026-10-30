// Pixelay: helper functions for full-brightness displays (15/15 lightmap).
// Used to bypass directional sunlight shading on 2D/3D canvas models when full brightness is desired.

#ifndef PIXELAY_LIGHTING_INCLUDED
#define PIXELAY_LIGHTING_INCLUDED

// GUI item model detection: orthographic projection has m23 == 0.0 (perspective projection has -1.0)
bool pixelay_is_gui(mat4 proj) {
    return proj[2][3] == 0.0;
}

// First person hand item model detection
bool pixelay_is_hand(mat4 proj) {
    return abs(proj[3][2] + 0.10005) < 0.00001;
}

bool pixelay_is_full_brightness(ivec2 uv2, mat4 proj) {
    // Never strip lighting or shadows from inventory GUI slots or first-person hand
    if (pixelay_is_gui(proj) || pixelay_is_hand(proj)) {
        return false;
    }
    return uv2 == ivec2(240, 240);
}

void pixelay_apply_full_brightness(inout vec4 outVertexColor, inout vec4 outLightColor, vec4 tintColor) {
    outVertexColor = tintColor;
#ifndef EMISSIVE
    outLightColor = vec4(1.0);
#endif
}

#endif // PIXELAY_LIGHTING_INCLUDED
