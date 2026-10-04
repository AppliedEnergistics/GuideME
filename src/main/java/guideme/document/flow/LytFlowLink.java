package guideme.document.flow;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import guideme.PageAnchor;
import guideme.color.SymbolicColor;
import guideme.internal.GuideMEClient;
import guideme.ui.GuideUiHost;
import java.net.URI;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

public class LytFlowLink extends LytTooltipSpan {
    @Nullable
    private Consumer<GuideUiHost> clickCallback;

    @Nullable
    private SoundEvent clickSound = GuideMEClient.GUIDE_CLICK_EVENT;

    public LytFlowLink() {
        modifyStyle(style -> style.color(SymbolicColor.LINK));
        modifyHoverStyle(style -> style.underlined(true));
    }

    public void setClickCallback(@Nullable Consumer<GuideUiHost> clickCallback) {
        this.clickCallback = clickCallback;
    }

    @Override
    public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
        if (button.button() == InputConstants.MOUSE_BUTTON_LEFT && clickCallback != null) {
            if (clickSound != null) {
                var handler = Minecraft.getInstance().getSoundManager();
                handler.play(SimpleSoundInstance.forUI(clickSound, 1.0F));
            }
            clickCallback.accept(screen);
            return true;
        }
        return false;
    }

    public @Nullable SoundEvent getClickSound() {
        return clickSound;
    }

    public void setClickSound(@Nullable SoundEvent clickSound) {
        this.clickSound = clickSound;
    }

    /**
     * Configures this link to open the given external URL on click.
     */
    public void setExternalUrl(URI uri) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("External URLs must be absolute: " + uri);
        }

        setClickCallback(screen -> {
            var mc = Minecraft.getInstance();
            var previousScreen = mc.gui.screen();
            mc.gui.setScreen(new ConfirmLinkScreen(yes -> {
                if (yes) {
                    Blaze3D.openUri(uri);
                }

                mc.gui.setScreen(previousScreen);
            }, uri, true));
        });
    }

    /**
     * Configures this link to open the given page on click.
     */
    public void setPageLink(PageAnchor anchor) {
        setClickCallback(screen -> {
            screen.navigateTo(anchor);
        });
    }
}
