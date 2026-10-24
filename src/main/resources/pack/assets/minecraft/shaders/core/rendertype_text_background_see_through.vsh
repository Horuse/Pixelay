#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:pixelay_cursor_config.glsl>
#moj_import <minecraft:pixelay_state.glsl>

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    // Pixelay: screen sessions draw with the canvas' field of view (see pixelay_state.glsl).
    gl_Position = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(Position, 1.0), ProjMat);

    vertexColor = Color;
}
