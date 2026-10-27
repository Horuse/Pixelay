#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:pixelay_state.glsl>
#moj_import <minecraft:pixelay_lighting.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    // Pixelay: screen sessions draw with the canvas' field of view (see pixelay_state.glsl).
    gl_Position = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(Position, 1.0), ProjMat);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = pixelay_is_full_brightness(UV2) ? Color : Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
