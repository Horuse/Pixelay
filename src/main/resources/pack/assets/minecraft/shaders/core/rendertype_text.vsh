#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:pixelay_clip.glsl>
#moj_import <minecraft:pixelay_state.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out vec4 pixelayClip;
out vec2 pixelayLocal;
flat out int pixelayTransitionType;
flat out float pixelayProgress;
flat out vec4 pixelayOverlayColor;

void main() {
    if (UV2.y / 16 == 13) {
        pixelayTransitionType = UV2.x / 16;
        pixelayProgress = Color.a;
        pixelayOverlayColor = vec4(Color.rgb, 1.0);

        int corner = gl_VertexID % 4;
        vec2 ndc;
        if (corner == 0) ndc = vec2(-1.0, -1.0);
        else if (corner == 1) ndc = vec2(-1.0, 1.0);
        else if (corner == 2) ndc = vec2(1.0, 1.0);
        else ndc = vec2(1.0, -1.0);

        gl_Position = vec4(ndc, -0.999, 1.0);
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        vertexColor = Color;
        texCoord0 = UV0;
        pixelayClip = vec4(0.0);
        pixelayLocal = vec2(0.0);
        return;
    }

    pixelayTransitionType = -1;
    pixelayProgress = 0.0;
    pixelayOverlayColor = vec4(0.0);

    gl_Position = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(Position, 1.0), ProjMat);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    pixelayClip = pixelay_clip_range(Color, ProjMat);
    // Quads are drawn 4 vertices each, so the vertex index tells the corner.
    pixelayLocal = PIXELAY_GLYPH_CORNERS[gl_VertexID % 4];
}
