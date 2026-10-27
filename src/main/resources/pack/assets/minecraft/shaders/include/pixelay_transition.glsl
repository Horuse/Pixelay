// Pixelay: screen transition shaders (fade, wipe, iris, dissolve, curtain).
// Can be imported into core shaders (e.g. position_tex_color or rendertype_text)
// to perform cinematic screen transitions on client hardware.

#ifndef PIXELAY_TRANSITION_INCLUDED
#define PIXELAY_TRANSITION_INCLUDED

// Procedural 2D hash for random noise dissolve without requiring external textures
float pixelay_noise(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 1. Simple Color Fade
// coverage: 0.0 = completely revealed (transparent), 1.0 = completely covered by overlayColor
vec4 pixelay_transition_fade(float coverage, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(coverage, 0.0, 1.0);
    return mix(screenColor, overlayColor, t);
}

// 2. Circle / Iris Wipe
// Expands from center outward on reveal (coverage -> 0.0), contracts inward to center on cover (coverage -> 1.0)
// uv: normalized 0.0 .. 1.0 screen coordinates
vec4 pixelay_transition_circle(vec2 uv, float coverage, float aspectRatio, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(coverage, 0.0, 1.0);
    vec2 center = vec2(0.5, 0.5);
    vec2 delta = (uv - center) * vec2(aspectRatio, 1.0);
    float dist = length(delta);

    // Max radius to cover all corners
    float maxRadius = length(vec2(aspectRatio, 1.0)) * 0.55;
    float currentRadius = (1.0 - t) * maxRadius;

    float mask = smoothstep(currentRadius - 0.02, currentRadius + 0.02, dist);
    return mix(screenColor, overlayColor, mask);
}

// 3. Directional Linear Wipe
// coord: position along direction axis in 0.0 .. 1.0
vec4 pixelay_transition_linear_axis(float coord, float coverage, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(coverage, 0.0, 1.0);
    float edge = t * 1.1 - 0.05;
    float mask = smoothstep(edge + 0.03, edge - 0.03, coord);
    return mix(screenColor, overlayColor, mask);
}

vec4 pixelay_transition_linear(vec2 uv, float coverage, int direction, vec4 screenColor, vec4 overlayColor) {
    float coord;
    if (direction == 2) {
        coord = 1.0 - uv.x; // LEFT
    } else if (direction == 3) {
        coord = uv.x;       // RIGHT
    } else if (direction == 4) {
        coord = 1.0 - uv.y; // DOWN
    } else {
        coord = uv.y;       // UP (5)
    }
    return pixelay_transition_linear_axis(coord, coverage, screenColor, overlayColor);
}

// 4. Procedural Dissolve Wipe
// Pixel-by-pixel noise dissolve with 4x4 pixel grid blocks
vec4 pixelay_transition_dissolve(vec2 uv, vec2 screenSize, float coverage, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(coverage, 0.0, 1.0);
    vec2 gridCoord = floor(uv * (screenSize / 4.0));
    float n = pixelay_noise(gridCoord);
    float mask = step(n, t);
    return mix(screenColor, overlayColor, mask);
}

// 5. Curtain / Split Wipe
// Splits open from center horizontally on reveal (coverage -> 0.0)
vec4 pixelay_transition_curtain(vec2 uv, float coverage, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(coverage, 0.0, 1.0);
    float dist = abs(uv.x - 0.5) * 2.0; // 0.0 at center, 1.0 at edges
    float openDist = (1.0 - t) * 1.05 - 0.025;
    float mask = smoothstep(openDist - 0.03, openDist + 0.03, dist);
    return mix(screenColor, overlayColor, mask);
}

#endif // PIXELAY_TRANSITION_INCLUDED
