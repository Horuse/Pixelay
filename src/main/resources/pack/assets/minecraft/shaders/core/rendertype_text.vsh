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
    // Single exit, no early return and no branch: transition quads are handled with selects only.
    // Early returns with a replaced gl_Position break text on NVIDIA drivers.
    bool isTrans = pixelay_is_trans(Color, ProjMat);
    bool isTransShadow = pixelay_is_trans_shadow(Color, ProjMat);

    // Pixelay: screen sessions draw with the canvas field of view (see pixelay_state.glsl).
    vec4 normalPos = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(Position, 1.0), ProjMat);

    // Quads have 4 vertices each, so the vertex index selects the corner.
    vec2 corner = PIXELAY_GLYPH_CORNERS[gl_VertexID % 4];
    // Full-screen quad in NDC: (-1, 1) top-left to (1, -1) bottom-right.
    vec4 transPos = vec4(corner.x * 2.0 - 1.0, 1.0 - corner.y * 2.0, normalPos.z, 1.0);
    // Shadow quad of the transition title is collapsed outside NDC.
    vec4 hiddenPos = vec4(2.0, 2.0, 2.0, 1.0);

    gl_Position = isTrans ? transPos : (isTransShadow ? hiddenPos : normalPos);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = isTrans ? Color : Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;

    pixelayClip = pixelay_clip_range(Color, ProjMat);
    pixelayLocal = corner;

    pixelayTransition = isTrans ? 1 : 0;
    ivec3 c = ivec3(round(Color.rgb * 255.0));
    pixelayTransParams = ivec2(c.g, c.b);
    pixelayScreenUV = corner;
}
