package guideme.internal.screen;

import guideme.Guide;
import guideme.PageAnchor;
import java.net.URI;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the history navigation logic from GuideScreen to allow for jumping between search and guide display
 * seamlessly without duplicating all the nav logic.
 */
public final class GuideNavigation {
    private static final Logger LOG = LoggerFactory.getLogger(GuideNavigation.class);

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

        // Handle navigation within the same guide
        if (currentScreen instanceof GuideScreen guideScreen && guideScreen.getGuide() == guide) {
            if (Objects.equals(guideScreen.getCurrentPageId(), anchor.pageId())) {
                guideScreen.scrollToAnchor(anchor.anchor());
                if (anchor.anchor() != null) {
                    history.push(anchor);
                }
            } else {
                guideScreen.loadPageAndScrollTo(anchor);
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

    public static void openUrl(String href) {
        URI uri;
        try {
            uri = URI.create(href);
        } catch (IllegalArgumentException ignored) {
            LOG.debug("Can't parse '{}' as URL", href);
            return;
        }

        // Treat it as an external URL if it has a scheme
        var minecraft = Minecraft.getInstance();
        var previousScreen = minecraft.screen;

        if (uri.getScheme() != null) {
            if (minecraft.options.chatLinksPrompt().get().booleanValue()) {
                minecraft.setScreen(new ConfirmLinkScreen(doOpen -> {
                    if (doOpen) {
                        Util.getPlatform().openUri(uri);
                    }
                    minecraft.setScreen(previousScreen);
                }, href, false));
            } else {
                Util.getPlatform().openUri(uri);
            }
        } else {
            LOG.debug("Can't open relative URL: '{}'", href);
        }
    }
}
