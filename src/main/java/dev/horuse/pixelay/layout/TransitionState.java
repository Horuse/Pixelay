package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.utils.ColorUtils;

/**
 * Tracks interpolation state for color transitions.
 */
public final class TransitionState {

    private final Transition transition;
    private int fromColor;
    private int toColor;
    private int currentColor;
    private boolean initialized;
    private boolean animating;
    private long startTime;

    public TransitionState(Transition transition) {
        this.transition = transition;
    }

    public int update(int targetColor, long now) {
        if (!initialized) {
            fromColor = targetColor;
            toColor = targetColor;
            currentColor = targetColor;
            initialized = true;
            animating = false;
            return currentColor;
        }

        if (targetColor != toColor) {
            fromColor = currentColor;
            toColor = targetColor;
            startTime = now;
            animating = true;
        }

        if (animating) {
            long duration = transition.durationMs();
            if (duration <= 0) {
                currentColor = toColor;
                animating = false;
            } else {
                long elapsed = now - startTime;
                if (elapsed >= duration) {
                    currentColor = toColor;
                    animating = false;
                } else {
                    double progress = (double) elapsed / duration;
                    double eased = transition.easing().ease(progress);
                    currentColor = ColorUtils.lerp(fromColor, toColor, eased);
                }
            }
        }

        return currentColor;
    }

    public boolean isAnimating() {
        return animating;
    }

    public int currentColor() {
        return currentColor;
    }

    public Transition transition() {
        return transition;
    }
}
