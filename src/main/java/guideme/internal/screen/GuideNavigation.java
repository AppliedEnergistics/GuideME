package guideme.internal.screen;

import guideme.Guide;
import guideme.PageAnchor;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts the history navigation logic from GuideScreen to allow for jumping between search and guide display
 * seamlessly without duplicating all the nav logic.
 */
public final class GuideNavigation {
    private GuideNavigation() {
    }

    public static void navigateTo(Guide guide, PageAnchor anchor) {
        var history = GlobalInMemoryHistory.get(guide);
        var currentScreen = getCurrentGuideMeScreen();
        Screen screenToReturnTo = null;
        if (currentScreen instanceof GuideScreen guideScreen) {
            screenToReturnTo = guideScreen.getReturnToOnClose();
        } else if (currentScreen instanceof GuideSearchScreen searchScreen) {
            screenToReturnTo = searchScreen.getReturnToOnClose();
        }

        // Handle navigation within the same page
        if (currentScreen instanceof GuideScreen guideScreen && guideScreen.getGuide() == guide
                && Objects.equals(guideScreen.getCurrentPageId(), anchor.pageId())) {
            guideScreen.scrollToAnchor(anchor.anchor());
            if (anchor.anchor() != null) {
                history.push(anchor);
            }
            return;
        }

        // Handle built-in pages
        if (GuideSearchScreen.PAGE_ID.equals(anchor.pageId())) {
            var guiScreen = GuideSearchScreen.open(guide, anchor.anchor());
            guiScreen.setReturnToOnClose(screenToReturnTo);
            Minecraft.getInstance().setScreen(guiScreen);
            return;
        }

        GuideScreen guideScreen = GuideScreen.openNew(guide, anchor, history);
        guideScreen.setReturnToOnClose(screenToReturnTo);
        Minecraft.getInstance().setScreen(guideScreen);
    }

    @Nullable
    private static Screen getCurrentGuideMeScreen() {
        var currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof GuideScreen || currentScreen instanceof GuideSearchScreen) {
            return currentScreen;
        }
        return null;
    }

    public static void navigateForward(Guide guide) {
        var history = GlobalInMemoryHistory.get(guide);
        history.forward().ifPresent(pageAnchor -> navigateTo(guide, pageAnchor));
    }

    public static void navigateBack(Guide guide) {
        var history = GlobalInMemoryHistory.get(guide);
        history.back().ifPresent(pageAnchor -> navigateTo(guide, pageAnchor));
    }
}
