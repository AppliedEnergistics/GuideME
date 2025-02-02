package guideme.internal.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

/**
 * Really the only reason we have to use this subclass is that the scissor methods work in directly scaled screen
 * coordinates.
 */
final class ScaledGuiGraphics extends GuiGraphics {
    private final float scale;

    public ScaledGuiGraphics(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource bufferSource,
            float scale) {
        super(minecraft, pose, bufferSource);
        this.scale = scale;
    }

    @Override
    public int guiWidth() {
        return (int) (super.guiWidth() / scale);
    }

    @Override
    public int guiHeight() {
        return (int) (super.guiHeight() / scale);
    }

    @Override
    protected void applyScissor(@Nullable ScreenRectangle rectangle) {
        // Transform rectangle if additional scale is applied
        if (rectangle != null) {
            rectangle = new ScreenRectangle(
                    (int) Math.floor(rectangle.left() * scale),
                    (int) Math.floor(rectangle.top() * scale),
                    (int) Math.ceil(rectangle.width() * scale),
                    (int) Math.ceil(rectangle.height() * scale));
        }
        super.applyScissor(rectangle);
    }
}
