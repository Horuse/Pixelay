// Pixelay: canvas texts cut at their clip (CSS overflow), glyph by glyph. The plugin hides in the colour of
// every glyph of a cut text (its 3 lowest bits of red, green and blue) how much of it to drop, and sends the
// text with opacity 254 so that only those texts are decoded. See TextClip in the plugin.

#define PIXELAY_CODED_ALPHA 254.0

// Corners of a glyph quad in the order the client emits its vertices: top-left, bottom-left, bottom-right,
// top-right, in glyph space (x right, y down). Vertex shaders pick theirs with gl_VertexID % 4.
const vec2 PIXELAY_GLYPH_CORNERS[4] = vec2[4](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));

// Kept part of a glyph from one 4-bit cut value: 1-7 drop the first k/8, 9-15 keep the first (k-8)/8.
vec2 pixelay_kept(int cut) {
    if (cut >= 1 && cut <= 7) {
        return vec2(float(cut) / 8.0, 1.0);
    }
    if (cut >= 9) {
        return vec2(0.0, float(cut - 8) / 8.0);
    }
    return vec2(0.0, 1.0);
}

// The kept part of this vertex' glyph, (left, top, right, bottom) in glyph space; the whole glyph when the
// text is not cut. Only texts drawn in the world (perspective projection) are decoded, never GUI texts.
vec4 pixelay_clip_range(vec4 color, mat4 projection) {
    bool world = projection[2][3] != 0.0;
    if (!world || abs(color.a * 255.0 - PIXELAY_CODED_ALPHA) > 0.5) {
        return vec4(0.0, 0.0, 1.0, 1.0);
    }
    ivec3 c = ivec3(color.rgb * 255.0 + 0.5);
    int code = (c.r & 7) | ((c.g & 7) << 3) | ((c.b & 7) << 6);
    if ((code & 256) == 0) {
        return vec4(0.0, 0.0, 1.0, 1.0);
    }
    int horizontal = code & 15;
    int vertical = (code >> 4) & 15;
    if (horizontal == 8) {
        // Hidden: an empty range.
        return vec4(2.0, 2.0, -1.0, -1.0);
    }
    vec2 x = pixelay_kept(horizontal);
    vec2 y = pixelay_kept(vertical);
    return vec4(x.x, y.x, x.y, y.y);
}

// Whether a point of a glyph (in glyph space) is cut away.
bool pixelay_clipped(vec4 range, vec2 local) {
    return local.x < range.x || local.y < range.y || local.x > range.z || local.y > range.w;
}
