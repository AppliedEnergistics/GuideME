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
}
