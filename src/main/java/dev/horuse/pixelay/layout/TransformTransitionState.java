package dev.horuse.pixelay.layout;

/**
 * Tracks interpolation state for transform transitions.
 */
public final class TransformTransitionState {

    private final Transition transition;
    private Transform from = Transform.NONE;
    private Transform to = Transform.NONE;
    private Transform current = Transform.NONE;
    private boolean initialized;
    private boolean animating;
    private long startTime;

    public TransformTransitionState(Transition transition) {
        this.transition = transition;
    }

    public Transform update(Transform target, long now) {
        if (target == null) {
            target = Transform.NONE;
        }
        if (!initialized) {
            from = target;
            to = target;
            current = target;
            initialized = true;
            animating = false;
            return current;
        }

        if (!target.approximatelyEquals(to)) {
            from = current;
            to = target;
            startTime = now;
            animating = true;
        }

        if (animating) {
            long duration = transition.durationMs();
            if (duration <= 0) {
                current = to;
                animating = false;
            } else {
                long elapsed = now - startTime;
                if (elapsed >= duration) {
                    current = to;
                    animating = false;
                } else {
                    double progress = (double) elapsed / duration;
                    double eased = transition.easing().ease(progress);
                    current = from.lerp(to, eased);
                }
            }
        }

        return current;
    }

    public boolean isAnimating() {
        return animating;
    }

    public Transform current() {
        return current;
    }

    public Transition transition() {
        return transition;
    }
}
