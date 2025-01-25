package guideme.ui;

import guideme.PageAnchor;
import guideme.document.LytPoint;
import guideme.document.LytRect;
import guideme.document.interaction.InteractiveElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface DocumentUiHost {
    void navigateTo(ResourceLocation pageId);

    void navigateTo(PageAnchor anchor);

    @Nullable
    UiPoint getDocumentPoint(double screenX, double screenY);

    UiPoint getDocumentPointUnclamped(double screenX, double screenY);

    LytPoint getScreenPoint(LytPoint documentPoint);

    LytRect getDocumentRect();

    LytRect getDocumentViewport();

    @Nullable
    InteractiveElement getMouseCaptureTarget();

    void captureMouse(InteractiveElement element);

    void releaseMouseCapture(InteractiveElement element);
}
