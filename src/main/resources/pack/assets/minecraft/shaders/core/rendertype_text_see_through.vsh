#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:pixelay_clip.glsl>
#moj_import <minecraft:pixelay_state.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord0;
flat out vec4 pixelayClip;
out vec2 pixelayLocal;

void main() {
    gl_Position = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(Position, 1.0), ProjMat);

    vertexColor = Color;
    texCoord0 = UV0;
    pixelayClip = pixelay_clip_range(Color, ProjMat);
    // Quads are drawn 4 vertices each, so the vertex index tells the corner.
    pixelayLocal = PIXELAY_GLYPH_CORNERS[gl_VertexID % 4];
}
