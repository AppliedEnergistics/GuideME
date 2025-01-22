package guideme.document.interaction;

import guideme.ui.GuideUiHost;
import java.util.Optional;

public interface InteractiveElement {
    default boolean mouseMoved(GuideUiHost screen, int x, int y) {
        return false;
    }

    default boolean mouseClicked(GuideUiHost screen, int x, int y, int button) {
        return false;
    }

    default boolean mouseReleased(GuideUiHost screen, int x, int y, int button) {
        return false;
    }

    default void mouseCaptureLost() {
    }

    /**
     * @param x X position of the mouse in document coordinates.
     * @param y Y position of the mouse in document coordinates.
     */
    default Optional<GuideTooltip> getTooltip(float x, float y) {
        return Optional.empty();
    }
}
