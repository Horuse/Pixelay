#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:pixelay_clip.glsl>
#moj_import <minecraft:pixelay_transition.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
flat in vec4 pixelayClip;
in vec2 pixelayLocal;
flat in int pixelayTransition;
flat in ivec2 pixelayTransParams;
in vec2 pixelayScreenUV;

out vec4 fragColor;

void main() {
    if (pixelayTransition == 1) {
        vec4 transColor;
        if (!pixelay_trans_frag(pixelayScreenUV, vertexColor.a, pixelayTransParams, transColor)) {
            discard;
        }
        fragColor = transColor;
        return;
    }

    if (pixelay_clipped(pixelayClip, pixelayLocal)) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
