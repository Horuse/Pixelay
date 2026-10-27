// Pixelay: helper functions for full-brightness displays (15/15 lightmap).
// Used to bypass directional sunlight shading on 2D/3D canvas models when full brightness is desired.

#ifndef PIXELAY_LIGHTING_INCLUDED
#define PIXELAY_LIGHTING_INCLUDED

bool pixelay_is_full_brightness(ivec2 uv2) {
    return uv2 == ivec2(240, 240);
}

void pixelay_apply_full_brightness(inout vec4 outVertexColor, inout vec4 outLightColor, vec4 tintColor) {
    outVertexColor = tintColor;
#ifndef EMISSIVE
    outLightColor = vec4(1.0);
#endif
}

#endif // PIXELAY_LIGHTING_INCLUDED
