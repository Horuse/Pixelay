// Pixelay: screen transition shaders (fade, wipe, iris, dissolve).
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
// progress: 0.0 = 100% overlay (e.g. black), 1.0 = 100% screen content (fully visible)
vec4 pixelay_transition_fade(float progress, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(progress, 0.0, 1.0);
    return mix(overlayColor, screenColor, t);
}

// 2. Circle / Iris Wipe
// Expands from screen center outward (or contracts inward on close)
// uv: normalized 0.0 .. 1.0 screen coordinates
vec4 pixelay_transition_circle(vec2 uv, float progress, float aspectRatio, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(progress, 0.0, 1.0);
    vec2 center = vec2(0.5, 0.5);
    vec2 delta = (uv - center) * vec2(aspectRatio, 1.0);
    float dist = length(delta);

    // Max radius from center to corner
    float maxRadius = length(vec2(aspectRatio, 1.0)) * 0.55;
    float currentRadius = t * maxRadius;

    // Smooth 0.02 antialiased boundary edge
    float mask = smoothstep(currentRadius - 0.02, currentRadius + 0.02, dist);
    return mix(screenColor, overlayColor, mask);
}

// 3. Directional Linear Wipe
// dir: normalized direction (e.g. vec2(1.0, 0.0) for left-to-right wipe)
vec4 pixelay_transition_linear(vec2 uv, float progress, vec2 dir, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(progress, 0.0, 1.0);
    float proj = dot(uv, normalize(dir));
    float edge = t * 1.2 - 0.1;
    float mask = smoothstep(edge - 0.05, edge + 0.05, proj);
    return mix(screenColor, overlayColor, mask);
}

// 4. Procedural Dissolve Wipe
// Pixel-by-pixel noise dissolve with glowing/soft boundary
vec4 pixelay_transition_dissolve(vec2 uv, vec2 screenSize, float progress, vec4 screenColor, vec4 overlayColor) {
    float t = clamp(progress, 0.0, 1.0);
    // Blocky or fine noise grid based on screen resolution
    vec2 gridCoord = floor(uv * (screenSize / 4.0));
    float n = pixelay_noise(gridCoord);

    float mask = step(t, n);
    return mix(screenColor, overlayColor, mask);
}

// 5. Custom Texture Mask Wipe (Reference / Extensibility)
// In custom packs with dedicated transition mask textures (e.g. spiral, diamond, water ripple):
// sampler: grayscale mask texture where black (0.0) transitions first and white (1.0) transitions last
// float maskValue = texture(maskSampler, uv).r;
// float mask = smoothstep(progress - 0.05, progress + 0.05, maskValue);
// return mix(screenColor, overlayColor, mask);

#endif // PIXELAY_TRANSITION_INCLUDED
