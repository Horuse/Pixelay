// Pixelay: the state of a screen session, sent by the server as a negative world age (GameTime). 0 = no
// screen. Otherwise 1 + sprite + 8 * level: the cursor sprite (7 = hidden) and the level of the canvas size.
// The client counts the age on between packets: the server sends the middle of a state's range, rounding
// absorbs PIXELAY_STATE_STEP / 2 ticks of lag.
//
// Needs globals.glsl only, so that other packs' shaders can import it too (a copy of it, if they must work
// without Pixelay's pack). The constants match PixelayPack in the plugin.

#define PIXELAY_DAY_TICKS 24000.0
#define PIXELAY_STATE_STEP 32.0
#define PIXELAY_LEVELS 93
#define PIXELAY_TAN_MIN 0.12
#define PIXELAY_TAN_MAX 3.0

int pixelay_state(float gameTime) {
    float ticks = -gameTime * PIXELAY_DAY_TICKS;
    if (ticks < PIXELAY_STATE_STEP * 0.5) {
        return 0;
    }
    return int(floor(ticks / PIXELAY_STATE_STEP + 0.5));
}

int pixelay_sprite(int state) {
    return (state - 1) % 8;
}

int pixelay_level(int state) {
    return min((state - 1) / 8, PIXELAY_LEVELS - 1);
}

// Tangent of half the canvas height as the camera must see it (log scale over the levels).
float pixelay_half_height(int level) {
    float t = float(level) / float(PIXELAY_LEVELS - 1);
    return PIXELAY_TAN_MIN * pow(PIXELAY_TAN_MAX / PIXELAY_TAN_MIN, t);
}

// During a screen session, world geometry is projected with the field of view the canvas was made for, not
// the player's own one: the canvas fills the screen whatever FOV the player plays with, and the cursor (drawn
// with the same field of view) is always over the pixel it clicks. GUI (orthographic) is left alone.
vec4 pixelay_screen_projection(vec4 clip, mat4 projection) {
    int state = pixelay_state(GameTime);
    if (state == 0 || projection[2][3] == 0.0) {
        return clip;
    }
    float halfHeight = pixelay_half_height(pixelay_level(state));
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    clip.x *= 1.0 / (halfHeight * aspect * projection[0][0]);
    clip.y *= 1.0 / (halfHeight * projection[1][1]);
    return clip;
}
