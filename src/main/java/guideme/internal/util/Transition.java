package guideme.internal.util;

import org.lwjgl.glfw.GLFW;

/**
 * Models transitions for numeric values.
 */
public final class Transition {
    private static final double EPSILON = 0.0001; // Values differing less than this are considered equal
    private static final double MIN_UPDATE_DURATION = 0.005; // 5 millisecond

    // The Min/Max range of the value. Used to determine the time for a full transition
    private final double valueMin;
    private final double valueMax;
    private final double fullDuration;
    private final double speed;

    private double fullTransitionTime;

    private final ValueGetter getter;
    private final ValueSetter setter;

    private Ticker ticker = Ticker.SYSTEM;

    private double lastUpdated = Double.NaN;
    private double currentTarget = Double.NaN;

    /**
     * @param valueMin
     * @param valueMax
     * @param getter
     * @param setter
     * @param fullDuration Time for a full transition from min to max or vice-versa.
     */
    public Transition(double valueMin, double valueMax, double fullDuration, ValueGetter getter, ValueSetter setter) {
        this.valueMin = valueMin;
        this.valueMax = valueMax;
        if (valueMax < valueMin) {
            throw new IllegalArgumentException("Max cannot be smaller than min.");
        }
        this.getter = getter;
        this.setter = setter;
        this.fullDuration = fullDuration;
        if (this.fullDuration <= 0) {
            throw new IllegalArgumentException("Full duration must be a positive number");
        }
        this.speed = (valueMax - valueMin) / fullDuration;
    }

    public void set(double targetValue) {
        currentTarget = targetValue;
    }

    public void update() {
        if (Double.isNaN(currentTarget)) {
            return; // No target set
        }

        var timeNow = ticker.currentSeconds();
        double elapsed = timeNow - lastUpdated;
        lastUpdated = timeNow;
        if (elapsed < MIN_UPDATE_DURATION) {
            return; // Do not update too often otherwise we get too small fractions
        }

        var distanceTraveled = speed * elapsed;
        var currentValue = getter.get();
        if (currentTarget > currentValue) {
            currentValue += distanceTraveled;
            if (currentValue > currentTarget) {
                currentValue = currentTarget;
            }
        } else {
            currentValue -= distanceTraveled;
            if (currentValue < currentTarget) {
                currentValue = currentTarget;
            }
        }
        setter.set(currentValue);
    }

    @FunctionalInterface
    public interface Ticker {
        Ticker SYSTEM = GLFW::glfwGetTime;

        double currentSeconds();
    }

    @FunctionalInterface
    public interface ValueGetter {
        double get();
    }

    @FunctionalInterface
    public interface ValueSetter {
        void set(double value);
    }

    public void setTicker(Ticker ticker) {
        this.ticker = ticker;
    }

    private static boolean equal(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }
}
