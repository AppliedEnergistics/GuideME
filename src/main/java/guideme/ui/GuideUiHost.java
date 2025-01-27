package guideme.ui;

import guideme.PageAnchor;
import guideme.PageCollection;
import guideme.document.LytPoint;
import guideme.document.LytRect;
import guideme.document.interaction.InteractiveElement;
import guideme.internal.screen.GuideNavigation;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface GuideUiHost {
    void navigateTo(ResourceLocation pageId);

    void navigateTo(PageAnchor anchor);

    void reloadPage();

    default void openUrl(String href) {
        GuideNavigation.openUrl(href);
    }

    @Nullable
    UiPoint getDocumentPoint(double screenX, double screenY);

    UiPoint getDocumentPointUnclamped(double screenX, double screenY);

    LytPoint getScreenPoint(LytPoint documentPoint);

    LytRect getDocumentRect();

    LytRect getDocumentViewport();

    PageCollection getGuide();

    @Nullable
    InteractiveElement getMouseCaptureTarget();

    void captureMouse(InteractiveElement element);

    void releaseMouseCapture(InteractiveElement element);
}
