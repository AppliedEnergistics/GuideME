package guideme.internal.screen;

import guideme.Guide;
import guideme.PageAnchor;
import guideme.internal.GuideMEClient;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.jetbrains.annotations.Nullable;

public class NavigationToolbar {
    private static final int GAP = 5;

    @Nullable
    private final Guide guide;

    @Nullable
    private Runnable closeCallback;

    private Button backButton;
    private Button forwardButton;

    private int left;
    private GuideIconButton toggleFullWidthButton;

    public NavigationToolbar(@Nullable Guide guide) {
        this.guide = guide;
    }

    public void update() {
        if (GuideMEClient.instance().isFullWidthLayout()) {
            toggleFullWidthButton.setRole(GuideIconButton.Role.CLOSE_FULL_WIDTH_VIEW);
        } else {
            toggleFullWidthButton.setRole(GuideIconButton.Role.OPEN_FULL_WIDTH_VIEW);
        }

        if (guide != null) {
            var history = GlobalInMemoryHistory.get(guide);
            backButton.active = history.peekBack().isPresent();
            forwardButton.active = history.peekForward().isPresent();
        }
    }

    public void addToScreen(Consumer<AbstractWidget> addWidget, int topEdge, int rightEdge) {
        if (closeCallback != null) {
            var closeButton = new GuideIconButton(
                    rightEdge - GuideIconButton.WIDTH,
                    topEdge,
                    GuideIconButton.Role.CLOSE,
                    closeCallback);
            addWidget.accept(closeButton);
            rightEdge = closeButton.getX() - GAP;
        }

        toggleFullWidthButton = new GuideIconButton(
                rightEdge - GuideIconButton.WIDTH,
                topEdge,
                GuideIconButton.Role.OPEN_FULL_WIDTH_VIEW,
                this::toggleFullWidth);
        addWidget.accept(toggleFullWidthButton);
        rightEdge = toggleFullWidthButton.getX() - GAP;

        if (guide != null) {
            forwardButton = new GuideIconButton(
                    rightEdge - GuideIconButton.WIDTH,
                    topEdge,
                    GuideIconButton.Role.FORWARD,
                    () -> GuideNavigation.navigateForward(guide));
            addWidget.accept(forwardButton);
            rightEdge = forwardButton.getX() - GAP;

            backButton = new GuideIconButton(
                    rightEdge - GuideIconButton.WIDTH,
                    topEdge,
                    GuideIconButton.Role.BACK,
                    () -> GuideNavigation.navigateBack(guide));
            addWidget.accept(backButton);
            rightEdge = backButton.getX() - GAP;
        }

        var canSearch = !(Minecraft.getInstance().screen instanceof GuideSearchScreen);
        if (canSearch) {
            var searchButton = new GuideIconButton(
                    rightEdge - GuideIconButton.WIDTH,
                    topEdge,
                    GuideIconButton.Role.SEARCH,
                    this::startSearch);
            addWidget.accept(searchButton);
            rightEdge = searchButton.getX() - GAP;
        }

        left = rightEdge;
        update();
    }

    private void toggleFullWidth() {
        GuideMEClient.instance().setFullWidthLayout(!GuideMEClient.instance().isFullWidthLayout());
    }

    private void startSearch() {
        GuideNavigation.navigateTo(guide, PageAnchor.page(GuideSearchScreen.PAGE_ID));
    }

    public int getLeft() {
        return left;
    }

    public void setCloseCallback(@Nullable Runnable closeCallback) {
        this.closeCallback = closeCallback;
    }
}
