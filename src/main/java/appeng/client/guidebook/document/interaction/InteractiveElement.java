package appeng.client.guidebook.document.interaction;

import appeng.client.guidebook.screen.GuideScreen;
import java.util.Optional;

public interface InteractiveElement {
    default boolean mouseMoved(GuideScreen screen, int x, int y) {
        return false;
    }

    default boolean mouseClicked(GuideScreen screen, int x, int y, int button) {
        return false;
    }

    default boolean mouseReleased(GuideScreen screen, int x, int y, int button) {
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
