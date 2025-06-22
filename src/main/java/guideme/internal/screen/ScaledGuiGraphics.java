package guideme.internal.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3x2fStack;

/**
 * Really the only reason we have to use this subclass is that the scissor methods work in directly scaled screen
 * coordinates.
 */
@ApiStatus.Internal
public final class ScaledGuiGraphics extends GuiGraphics {
    private final float scale;

    public ScaledGuiGraphics(Minecraft minecraft,
            Matrix3x2fStack pose,
            GuiRenderState renderState,
            float scale) {
        super(minecraft, pose, renderState);
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

    // TODO 1.21.6 @Override
    // TODO 1.21.6 protected void applyScissor(@Nullable ScreenRectangle rectangle) {
    // TODO 1.21.6 // Transform rectangle if additional scale is applied
    // TODO 1.21.6 if (rectangle != null) {
    // TODO 1.21.6 rectangle = new ScreenRectangle(
    // TODO 1.21.6 (int) Math.floor(rectangle.left() * scale),
    // TODO 1.21.6 (int) Math.floor(rectangle.top() * scale),
    // TODO 1.21.6 (int) Math.ceil(rectangle.width() * scale),
    // TODO 1.21.6 (int) Math.ceil(rectangle.height() * scale));
    // TODO 1.21.6 }
    // TODO 1.21.6 super.applyScissor(rectangle);
    // TODO 1.21.6 }
}
