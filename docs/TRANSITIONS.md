# Screen Transitions & Native Client GPU Interpolation in Pixelay

Pixelay provides two distinct layers of animation and transition technologies:
1. **Native Client GPU Interpolation (Unlimited / High-Refresh FPS)**: Sub-tick hardware GPU interpolation of transforms (`scale`, `translate`, `rotate`) on Display entities, running at the player's full monitor refresh rate (60 Hz, 144 Hz, 240 Hz, 360 Hz+).
2. **Screen Transitions**: Cinematic screen entry and exit transitions (fade, wipe, iris, dissolve) driven by both server-side faders and GLSL core shaders.

---

## 1. Native Client-Side GPU Interpolation (Unlimited FPS)

### Why Display Entities Normally Stutter at 20 FPS
In vanilla Minecraft, the server ticks at 20 TPS (50 ms per tick). If a plugin attempts to animate position or size by recalculating values and updating entity metadata on every server tick:
- The server sends intermediate steps at most 20 times per second.
- On a 60 Hz, 144 Hz, 240 Hz, or 360 Hz monitor, the animation appears choppy and stepped, like a 20 FPS slideshow.
- Setting `transformation_interpolation_duration = 0` forces the client to snap immediately to each packet, completely bypassing the client's internal GPU interpolation engine.

### How Pixelay Achieves Monitor-Refresh Smoothness
Minecraft 1.19.4+ Display entities (`TextDisplay`, `ItemDisplay`, `BlockDisplay`) natively support GPU sub-tick interpolation across render frames:
1. When a property changes (such as `:hover`, `:selected`, or `.scale(1.04)`), Pixelay calculates the transition duration in ticks:
   $$\text{durationTicks} = \max\left(1, \operatorname{round}\left(\frac{\text{durationMs}}{50}\right)\right)$$
2. Instead of recalculating intermediate steps on the server every tick, Pixelay immediately sends the **final target transformation matrix** with:
   - `transformation_interpolation_duration` = `durationTicks`
   - `transformation_start_delta` = `0`
3. The Minecraft client's `DisplayRenderer` interpolates between the current transformation matrix and target matrix on **every single video frame rendered by the GPU** using `partialTicks`.
4. The frame rate is completely **uncapped** and matches the player's monitor:
   - **60 Hz**: ~12 interpolated frames for a 200 ms transition
   - **144 Hz**: ~29 interpolated frames
   - **240 Hz**: ~48 interpolated frames
   - **360 Hz**: ~72 interpolated frames
   All rendered on the GPU with zero network overhead and zero server CPU load during the animation.

### How to Use Transitions in Pixelay Layouts

Transitions in Pixelay follow CSS syntax:

```java
Box<?> card = Layout.column(
        Layout.text(viewer, 24, "<b>Interactive Card</b>"),
        Layout.text(viewer, 18, "Floats upward and scales on hover")
    )
    .padding(20)
    .background(0xFFFFFFFF)
    .border(2, "#E2E8F0")
    // Define target style on :hover
    .state(State.HOVERED, s -> s
        .scale(1.04)
        .translateY(-4)
        .borderColor("#6366F1")
    )
    // 180 ms smooth transition (translates to 4 ticks of client GPU interpolation)
    .transition(180, Easing.EASE_OUT);
```

#### Transitioning Specific Properties
You can transition all properties or target specific ones:

```java
// Transitions all properties (transform and background)
.transition(200)

// Transitions only the transform (scale/translate/rotate)
.transition(Transition.Property.TRANSFORM, 200, Easing.EASE_OUT)

// Transitions only background color
.transition(Transition.Property.BACKGROUND, 250, Easing.EASE)
```

---

## 2. Screen Transitions (Fade In & Fade Out)

When opening or closing full-screen sessions (`ScreenSession`), instant popping can feel abrupt. Pixelay supports built-in screen transitions.

### Using ScreenSession Transitions

Configure transitions directly on the `ScreenSession.Builder`:

```java
ScreenSession session = ScreenSession.builder(player)
    .camera(eyeLocation)
    .resolution(1920, 1080)
    // 250ms fade-in from black when entering, 200ms fade-out to black when closing
    .fadeIn(250)
    .fadeOut(200)
    .content(canvas -> MyScreen.build(player))
    .open();
```

Or configure both at once:

```java
.transition(ScreenTransition.fade(200))
```

### Transition Types

Pixelay supports multiple transition effects defined in `ScreenTransition`:
- `ScreenTransition.fade(durationMs)` — Smooth color fade (default: pure black `#000000`).
- `ScreenTransition.circleWipe(durationMs)` — Circular iris expanding from center.
- `ScreenTransition.linearWipe(durationMs)` — Directional wipe sweeping across screen.

---

## 3. Shader Transitions (`pixelay_transition.glsl`)

For resource pack environments (such as custom core shaders or StarMine), Pixelay includes `assets/minecraft/shaders/include/pixelay_transition.glsl`.

### Available GLSL Functions

```glsl
#moj_import <minecraft:pixelay_transition.glsl>

// 1. Color Fade
vec4 pixelay_transition_fade(float progress, vec4 screenColor, vec4 overlayColor);

// 2. Circle / Iris Wipe
vec4 pixelay_transition_circle(vec2 uv, float progress, float aspectRatio, vec4 screenColor, vec4 overlayColor);

// 3. Directional Linear Wipe
vec4 pixelay_transition_linear(vec2 uv, float progress, vec2 dir, vec4 screenColor, vec4 overlayColor);

// 4. Procedural Noise Dissolve
vec4 pixelay_transition_dissolve(vec2 uv, vec2 screenSize, float progress, vec4 screenColor, vec4 overlayColor);
```

### Analysis: Complex Transitions with Custom Images & Animations

Can we perform complex transitions with custom images, textures, or animations? **Yes!** Here is how:

#### 1. Texture Mask Wipe (Grayscale Gradient Maps)
In game graphics (e.g. Zelda, Kingdom Hearts), wipes use a grayscale gradient texture as a threshold map:
- The texture contains a grayscale pattern (e.g. a swirl, diagonal stripes, or diamond gradient) where black is `0.0` and white is `1.0`.
- In the shader:
  ```glsl
  float mask = texture(TransitionMaskSampler, uv).r;
  float t = smoothstep(progress - 0.05, progress + 0.05, mask);
  fragColor = mix(screenColor, overlayColor, t);
  ```
- Any custom pattern can be designed in Photoshop/Figma as a 512×512 PNG.

#### 2. Sprite Sheet / Flipbook Animations
For animated logos, portal rings, or comic transitions:
- Put animation frames in an atlas (e.g. 4×4 grid = 16 frames).
- Derive the frame index from `progress`:
  ```glsl
  int frame = int(clamp(progress * 16.0, 0.0, 15.0));
  vec2 frameUV = (uv + vec2(frame % 4, frame / 4)) * 0.25;
  vec4 animTex = texture(SpriteAtlas, frameUV);
  ```

#### 3. GameTime Continuous Synchronization
Because `GameTime` includes `partialTick` on the client, time-based shader animations (`sin(GameTime)`, procedural ripples, CRT distortion) run at the player's monitor refresh rate (144+ FPS) with zero server bandwidth.
