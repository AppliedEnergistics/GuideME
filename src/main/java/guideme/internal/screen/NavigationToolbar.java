package guideme.internal.screen;

import guideme.Guide;
import guideme.PageAnchor;
import guideme.internal.GuideMEClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import org.jetbrains.annotations.Nullable;

public class NavigationToolbar {
    private static final int GAP = 5;

    @Nullable
    private final Guide guide;

    @Nullable
    private Runnable closeCallback;

    private boolean canSearch;

    private final GuideIconButton backButton;
    private final GuideIconButton forwardButton;
    private final GuideIconButton toggleFullWidthButton;
    private final GuideIconButton closeButton;
    private final GuideIconButton searchButton;

    private final List<GuideIconButton> buttons = new ArrayList<>();

    public NavigationToolbar(@Nullable Guide guide) {
        this.guide = guide;

        searchButton = new GuideIconButton(
                0,
                0,
                GuideIconButton.Role.SEARCH,
                this::startSearch);
        backButton = new GuideIconButton(
                0,
                0,
                GuideIconButton.Role.BACK,
                () -> GuideNavigation.navigateBack(guide));
        forwardButton = new GuideIconButton(
                0,
                0,
                GuideIconButton.Role.FORWARD,
                () -> GuideNavigation.navigateForward(guide));
        toggleFullWidthButton = new GuideIconButton(
                0,
                0,
                GuideIconButton.Role.OPEN_FULL_WIDTH_VIEW,
                this::toggleFullWidth);
        closeButton = new GuideIconButton(
                0,
                0,
                GuideIconButton.Role.CLOSE,
                () -> {
                    if (closeCallback != null) {
                        closeCallback.run();
                    }
                });

        update();
    }

    public void update() {
        updateLayout();

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

    public void addToScreen(Consumer<AbstractWidget> addWidget) {
        addWidget.accept(closeButton);
        addWidget.accept(toggleFullWidthButton);
        if (guide != null) {
            addWidget.accept(forwardButton);
            addWidget.accept(backButton);
        }
        if (canSearch) {
            addWidget.accept(searchButton);
        }
    }

    private void updateLayout() {
        buttons.clear();

        if (canSearch) {
            buttons.add(searchButton);
        }

        if (guide != null) {
            buttons.add(backButton);
            buttons.add(forwardButton);
        }

        buttons.add(toggleFullWidthButton);

        if (closeCallback != null) {
            buttons.add(closeButton);
        }
    }

    private void toggleFullWidth() {
        GuideMEClient.instance().setFullWidthLayout(!GuideMEClient.instance().isFullWidthLayout());
    }

    private void startSearch() {
        GuideNavigation.navigateTo(guide, PageAnchor.page(GuideSearchScreen.PAGE_ID));
    }

    public void setCloseCallback(@Nullable Runnable closeCallback) {
        this.closeCallback = closeCallback;
        update();
    }

    public boolean isCanSearch() {
        return canSearch;
    }

    public void setCanSearch(boolean canSearch) {
        this.canSearch = canSearch;
        update();
    }

    public int getWidth() {
        int width = 0;
        for (var button : buttons) {
            width += button.getWidth() + GAP;
        }
        return width;
    }

    public int getHeight() {
        return 16;
    }

    public void move(int x, int y) {
        for (var button : buttons) {
            button.setX(x);
            button.setY(y);
            x += button.getWidth() + GAP;
        }
    }

}
