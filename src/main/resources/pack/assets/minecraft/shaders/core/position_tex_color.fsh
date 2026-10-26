#version 330

#moj_import <minecraft:globals.glsl>

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
flat in int pixelayOverlay;
flat in vec2 pixelayLook;

out vec4 fragColor;

#moj_import <minecraft:pixelay_cursor_config.glsl>
#moj_import <minecraft:pixelay_state.glsl>
#moj_import <minecraft:pixelay_cursor.glsl>

void main() {
    if (pixelayOverlay == 1 && pixelay_atlas()) {
        int state = pixelay_state(GameTime);
        if (state == 0) {
            vec4 water = pixelay_water(texCoord0) * vertexColor;
            if (water.a == 0.0) {
                discard;
            }
            fragColor = water * ColorModulator;
            return;
        }
        vec4 cursor;
        if (pixelay_cursor(pixelayLook, state, ScreenSize, cursor)) {
            fragColor = cursor;
            return;
        }
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
