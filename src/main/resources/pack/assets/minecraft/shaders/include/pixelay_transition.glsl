// Pixelay: Screen-space Core Shader Transitions rendered above the hand, hearts, and hotbar.
// A full-screen quad is triggered by a Title using font pixelay:transition and signature Red = 126 (0x7E).

#define PIXELAY_TRANS_SIGNATURE 126

#define PIXELAY_TYPE_FADE 0
#define PIXELAY_TYPE_WIPE_RIGHT 1
#define PIXELAY_TYPE_WIPE_LEFT 2
#define PIXELAY_TYPE_WIPE_DOWN 3
#define PIXELAY_TYPE_WIPE_UP 4
#define PIXELAY_TYPE_CORNER_TL 5
#define PIXELAY_TYPE_CORNER_TR 6
#define PIXELAY_TYPE_CORNER_BL 7
#define PIXELAY_TYPE_CORNER_BR 8
#define PIXELAY_TYPE_CURTAIN 9

// Standard overlay palette
vec3 pixelay_trans_color(int idx) {
    if (idx == 1) return vec3(0.118, 0.106, 0.294); // #1E1B4B (Indigo)
    if (idx == 2) return vec3(0.059, 0.090, 0.165); // #0F172A (Slate)
    if (idx == 3) return vec3(1.0, 1.0, 1.0);       // #FFFFFF (White)
    if (idx == 4) return vec3(0.094, 0.094, 0.106); // #18181B (Zinc)
    if (idx == 5) return vec3(0.533, 0.075, 0.216); // #881337 (Rose)
    return vec3(0.0, 0.0, 0.0);                     // #000000 (Black)
}

// Check if this text quad is a Pixelay transition title
bool pixelay_is_trans(vec4 color, mat4 proj) {
    // Only in 2D GUI projection (where titles render above the HUD)
    if (proj[2][3] != 0.0) {
        return false;
    }
    int r = int(color.r * 255.0 + 0.5);
    return r == PIXELAY_TRANS_SIGNATURE;
}

// Check if this is the text shadow quad of a transition title
bool pixelay_is_trans_shadow(vec4 color, mat4 proj) {
    if (proj[2][3] != 0.0) {
        return false;
    }
    int r = int(color.r * 255.0 + 0.5);
    return r == (PIXELAY_TRANS_SIGNATURE >> 2); // 31
}

// Ease the linear time progress [0.0, 1.0]
float pixelay_trans_ease(float t, int easing) {
    t = clamp(t, 0.0, 1.0);
    if (easing == 1) { // EASE_IN_OUT
        return t < 0.5 ? 2.0 * t * t : 1.0 - pow(-2.0 * t + 2.0, 2.0) * 0.5;
    }
    if (easing == 2) { // EASE_IN
        return t * t;
    }
    if (easing == 3) { // EASE_OUT
        return 1.0 - (1.0 - t) * (1.0 - t);
    }
    if (easing == 4) { // SMOOTHSTEP
        return t * t * (3.0 - 2.0 * t);
    }
    return t; // LINEAR
}

// Computes the transition fragment color and alpha, or returns false to discard
bool pixelay_trans_frag(vec2 uv, float vertexAlpha, ivec2 params, out vec4 outColor) {
    int type = params.x & 15;
    int mode = (params.x >> 4) & 1; // 0 = IN (cover), 1 = OUT (reveal)
    int easing = (params.x >> 5) & 7;
    int colorIdx = params.y;

    vec3 col = pixelay_trans_color(colorIdx);
    // In Mode.IN: vertexAlpha goes 0.0 -> 1.0 during fadeIn.
    // In Mode.OUT: vertexAlpha goes 1.0 -> 0.0 during fadeOut.
    float rawProgress = (mode == 0) ? vertexAlpha : (1.0 - vertexAlpha);
    float t = pixelay_trans_ease(rawProgress, easing);

    if (type == PIXELAY_TYPE_FADE) {
        float alpha = (mode == 0) ? t : (1.0 - t);
        if (alpha <= 0.001) {
            return false;
        }
        outColor = vec4(col, alpha);
        return true;
    }

    float d = 0.0;
    if (type == PIXELAY_TYPE_WIPE_RIGHT) {
        d = uv.x;
    } else if (type == PIXELAY_TYPE_WIPE_LEFT) {
        d = 1.0 - uv.x;
    } else if (type == PIXELAY_TYPE_WIPE_DOWN) {
        d = uv.y;
    } else if (type == PIXELAY_TYPE_WIPE_UP) {
        d = 1.0 - uv.y;
    } else if (type == PIXELAY_TYPE_CORNER_TL) {
        d = (uv.x + uv.y) * 0.5;
    } else if (type == PIXELAY_TYPE_CORNER_TR) {
        d = ((1.0 - uv.x) + uv.y) * 0.5;
    } else if (type == PIXELAY_TYPE_CORNER_BL) {
        d = (uv.x + (1.0 - uv.y)) * 0.5;
    } else if (type == PIXELAY_TYPE_CORNER_BR) {
        d = ((1.0 - uv.x) + (1.0 - uv.y)) * 0.5;
    } else if (type == PIXELAY_TYPE_CURTAIN) {
        d = min(uv.x, 1.0 - uv.x) * 2.0;
    }

    // Sub-pixel smooth edge anti-aliasing with expanded margin so edges are 100% clean at boundaries
    float aa = 0.008;
    float threshold = mix(-aa * 2.0, 1.0 + aa * 2.0, t);
    float edge = smoothstep(threshold - aa, threshold + aa, d);
    float a = (mode == 0) ? (1.0 - edge) : edge;

    if (a <= 0.001) {
        return false;
    }
    outColor = vec4(col, a);
    return true;
}
