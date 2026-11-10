#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:pixelay_clip.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
flat in vec4 pixelayClip;
in vec2 pixelayLocal;

out vec4 fragColor;

void main() {
    if (pixelay_clipped(pixelayClip, pixelayLocal)) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color * ColorModulator;
}
