// Pixelay: clip text glyphs at canvas overflow boundaries.
// The plugin encodes cut parameters into the lowest 3 bits of red, green, and blue.
// The plugin sends cut text with opacity 254. See TextClip in the plugin.

#define PIXELAY_CODED_ALPHA 254

// A bounding box that does not discard fragments.
#define PIXELAY_NO_CLIP vec4(-1.0e4, -1.0e4, 1.0e4, 1.0e4)

// Glyph quad corners in the order the client emits vertices:
// top-left, bottom-left, bottom-right, top-right in glyph space (x right, y down).
const vec2 PIXELAY_GLYPH_CORNERS[4] = vec2[4](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));

// Returns the kept interval for one 4-bit cut value.
// Values 1 to 7 drop the first k/8.
// Values 9 to 15 keep the first (k-8)/8.
// Value 0 keeps the full interval.
vec2 pixelay_kept(int cut) {
    if (cut >= 1 && cut <= 7) {
        return vec2(float(cut) / 8.0, 1.0e4);
    }
    if (cut >= 9 && cut <= 15) {
        return vec2(-1.0e4, float(cut - 8) / 8.0);
    }
    return vec2(-1.0e4, 1.0e4);
}

// Returns the kept box (left, top, right, bottom) in glyph space.
// Returns PIXELAY_NO_CLIP when text is not cut.
// The shader decodes only world texts with perspective projection.
vec4 pixelay_clip_range(vec4 color, mat4 projection) {
    // Perspective projection check in Minecraft:
    // Perspective has m23 != 0 and m33 == 0.
    // Orthographic GUI has m23 == 0 and m33 == 1.
    bool world = projection[2][3] != 0.0 && projection[3][3] == 0.0;
    if (!world) {
        return PIXELAY_NO_CLIP;
    }
    int alpha = int(round(color.a * 255.0));
    if (alpha != PIXELAY_CODED_ALPHA) {
        return PIXELAY_NO_CLIP;
    }
    ivec3 c = ivec3(round(color.rgb * 255.0));
    int code = (c.r & 7) | ((c.g & 7) << 3) | ((c.b & 7) << 6);
    if ((code & 256) == 0) {
        return PIXELAY_NO_CLIP;
    }
    int horizontal = code & 15;
    int vertical = (code >> 4) & 15;
    if (horizontal == 8 || vertical == 8) {
        // Return an inverted range to discard the full glyph.
        return vec4(2.0, 2.0, -1.0, -1.0);
    }
    if (horizontal == 0 && vertical == 0) {
        return PIXELAY_NO_CLIP;
    }
    vec2 x = pixelay_kept(horizontal);
    vec2 y = pixelay_kept(vertical);
    return vec4(x.x, y.x, x.y, y.y);
}

// Returns true when a local point is outside the kept box.
bool pixelay_clipped(vec4 range, vec2 local) {
    return local.x < range.x || local.y < range.y || local.x > range.z || local.y > range.w;
}
