package guideme.ui;

import guideme.PageCollection;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface GuideUiHost extends DocumentUiHost {
    PageCollection getGuide();

    @Nullable
    ResourceLocation getCurrentPageId();

    void reloadPage();
}
