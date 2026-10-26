#version 330

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;
// Pixelay: whether this is the water overlay quad, and the player's look (yaw, pitch) in degrees.
flat out int pixelayOverlay;
flat out vec2 pixelayLook;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;

    // The water overlay: a full-screen quad at z -0.5 with alpha 0.1, its UV 0..4 shifted by the look.
    vec4 slack = abs(vec4(abs(Position.xy) - vec2(1.0), Position.z + 0.5, Color.a - 0.1));
    bool overlay = all(lessThan(slack, vec4(1.0e-4, 1.0e-4, 1.0e-4, 0.02)));
    pixelayOverlay = overlay ? 1 : 0;
    pixelayLook = overlay ? (UV0 - 4.0 * step(Position.xy, vec2(0.0))) * vec2(-64.0, 64.0) : vec2(0.0);
}
