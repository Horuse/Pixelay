#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:pixelay_transition.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
flat in int pixelayTransitionType;
flat in float pixelayProgress;
flat in vec4 pixelayOverlayColor;

out vec4 fragColor;

void main() {
    if (pixelayTransitionType >= 0) {
        vec2 uv = gl_FragCoord.xy / ScreenSize;
        float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
        vec4 result;
        if (pixelayTransitionType == 0) {
            result = pixelay_transition_fade(pixelayProgress, vec4(0.0), pixelayOverlayColor);
        } else if (pixelayTransitionType == 1) {
            result = pixelay_transition_circle(uv, pixelayProgress, aspect, vec4(0.0), pixelayOverlayColor);
        } else if (pixelayTransitionType >= 2 && pixelayTransitionType <= 5) {
            result = pixelay_transition_linear(uv, pixelayProgress, pixelayTransitionType, vec4(0.0), pixelayOverlayColor);
        } else if (pixelayTransitionType == 6) {
            result = pixelay_transition_dissolve(uv, ScreenSize, pixelayProgress, vec4(0.0), pixelayOverlayColor);
        } else if (pixelayTransitionType == 7) {
            result = pixelay_transition_curtain(uv, pixelayProgress, vec4(0.0), pixelayOverlayColor);
        } else {
            result = pixelay_transition_fade(pixelayProgress, vec4(0.0), pixelayOverlayColor);
        }
        if (result.a <= 0.01) {
            discard;
        }
        fragColor = result;
        return;
    }

    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
