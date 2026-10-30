#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:pixelay_clip.glsl>
#moj_import <minecraft:pixelay_state.glsl>
#moj_import <minecraft:pixelay_transition.glsl>

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
flat out int pixelayTransition;
flat out ivec2 pixelayTransParams;
out vec2 pixelayScreenUV;

void main() {
    if (pixelay_is_trans(Color, ProjMat)) {
        vec4 normalPos = ProjMat * ModelViewMat * vec4(Position, 1.0);
        vec2 corner = PIXELAY_GLYPH_CORNERS[gl_VertexID % 4];
        // Full-screen quad in NDC clip space: (-1, 1) top-left to (1, -1) bottom-right
        gl_Position = vec4(corner.x * 2.0 - 1.0, 1.0 - corner.y * 2.0, normalPos.z, 1.0);
        pixelayTransition = 1;
        ivec3 c = ivec3(Color.rgb * 255.0 + 0.5);
        pixelayTransParams = ivec2(c.g, c.b);
        pixelayScreenUV = corner;
        vertexColor = Color;
        texCoord0 = UV0;
        pixelayClip = vec4(0.0, 0.0, 1.0, 1.0);
        pixelayLocal = corner;
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        return;
    }

    if (pixelay_is_trans_shadow(Color, ProjMat)) {
        // Discard shadow quad of the transition title by collapsing it outside NDC
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        pixelayTransition = 0;
        pixelayTransParams = ivec2(0);
        pixelayScreenUV = vec2(0.0);
        vertexColor = vec4(0.0);
        texCoord0 = vec2(0.0);
        pixelayClip = vec4(0.0);
        pixelayLocal = vec2(0.0);
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        return;
    }

    pixelayTransition = 0;
    pixelayTransParams = ivec2(0);
    pixelayScreenUV = vec2(0.0);

    // Pixelay: screen sessions draw with the canvas' field of view (see pixelay_state.glsl).
    gl_Position = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(Position, 1.0), ProjMat);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    pixelayClip = pixelay_clip_range(Color, ProjMat);
    // Quads are drawn 4 vertices each, so the vertex index tells the corner.
    pixelayLocal = PIXELAY_GLYPH_CORNERS[gl_VertexID % 4];
}
