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
    bool isTrans = pixelay_is_trans(Color, ProjMat);
    bool isTransShadow = pixelay_is_trans_shadow(Color, ProjMat);

    vec3 pos = Position;
    vec2 ui = ceil(2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]));
    bool isGui = ProjMat[2][3] == 0.0 && ProjMat[3][3] == 1.0;
    bool isHud = false;

    // Detect ascent-encoded Screen HUD elements (pos.y >= ui.y in 2D GUI space)
    if (isGui && pos.y >= ui.y) {
        int bit = int(pos.y) >> 13;
        if (((bit >> 10) & 1) == 1) {
            isHud = true;
            int id = bit - 1024;
            // Cancel horizontal bossbar centering to set origin at left screen edge
            pos.x -= 0.5 * ui.x;
            // Restore true vertical position from encoded ascent
            pos.y -= float((bit << 13) + 4105);

            float xGui = 0.0;
            float yGui = 0.0;
            switch (id) {
                case 1: // TOP_LEFT
                    xGui = 0.0;
                    yGui = 0.0;
                    break;
                case 2: // TOP_CENTER
                    xGui = ui.x * 0.5;
                    yGui = 0.0;
                    break;
                case 3: // TOP_RIGHT
                    xGui = ui.x;
                    yGui = 0.0;
                    break;
                case 4: // CENTER_LEFT
                    xGui = 0.0;
                    yGui = ui.y * 0.5;
                    break;
                case 5: // CENTER
                    xGui = ui.x * 0.5;
                    yGui = ui.y * 0.5;
                    break;
                case 6: // CENTER_RIGHT
                    xGui = ui.x;
                    yGui = ui.y * 0.5;
                    break;
                case 7: // BOTTOM_LEFT
                    xGui = 0.0;
                    yGui = ui.y;
                    break;
                case 8: // BOTTOM_CENTER
                    xGui = ui.x * 0.5;
                    yGui = ui.y;
                    break;
                case 9: // BOTTOM_RIGHT
                    xGui = ui.x;
                    yGui = ui.y;
                    break;
                default:
                    break;
            }
            pos.x += xGui;
            pos.y += yGui;
        }
    }

    // Pixelay: screen sessions draw with the canvas field of view (see pixelay_state.glsl).
    vec4 normalPos = pixelay_screen_projection(ProjMat * ModelViewMat * vec4(pos, 1.0), ProjMat);

    // Quads have 4 vertices each, so the vertex index selects the corner.
    vec2 corner = PIXELAY_GLYPH_CORNERS[gl_VertexID % 4];
    // Full-screen quad in NDC: (-1, 1) top-left to (1, -1) bottom-right.
    vec4 transPos = vec4(corner.x * 2.0 - 1.0, 1.0 - corner.y * 2.0, normalPos.z, 1.0);
    // Shadow quad of transitions is collapsed outside NDC.
    vec4 hiddenPos = vec4(2.0, 2.0, 2.0, 1.0);

    gl_Position = isTrans ? transPos : (isTransShadow ? hiddenPos : normalPos);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vec4 baseColor = Color * texelFetch(Sampler2, UV2 / 16, 0);

    vertexColor = isTrans ? Color : baseColor;
    texCoord0 = UV0;

    pixelayClip = pixelay_clip_range(Color, ProjMat);
    pixelayLocal = corner;

    pixelayTransition = isTrans ? 1 : 0;
    ivec3 c = ivec3(round(Color.rgb * 255.0));
    pixelayTransParams = ivec2(c.g, c.b);
    pixelayScreenUV = corner;
}
