package guideme.document.block;

import guideme.color.ColorValue;
import guideme.color.ConstantColor;
import guideme.document.LytRect;
import guideme.document.LytSize;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.render.GuiSprite;
import guideme.render.RenderContext;
import java.util.Objects;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

/**
 * Render a {@link GuiSprite}.
 */
public class LytGuiSprite extends LytBlock implements InteractiveElement {

    @Nullable
    private GuiSprite sprite;

    private ColorValue color = ConstantColor.WHITE;

    private LytSize size = new LytSize(16, 16);

    public LytGuiSprite() {
    }

    public LytGuiSprite(GuiSprite sprite, LytSize size) {
        this.sprite = sprite;
        this.size = size;
    }

    @Nullable
    public GuiSprite getSprite() {
        return sprite;
    }

    public void setSprite(@Nullable GuiSprite sprite) {
        this.sprite = sprite;
    }

    public ColorValue getColor() {
        return color;
    }

    public void setColor(ColorValue color) {
        this.color = Objects.requireNonNullElse(color, ConstantColor.WHITE);
    }

    public LytSize getSize() {
        return size;
    }

    public void setSize(LytSize size) {
        this.size = size;
    }

    public void setSize(int width, int height) {
        setSize(new LytSize(width, height));
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        float actualWidth = size.width();
        float actualHeight = size.height();

        if (actualWidth > availableWidth) {
            var f = availableWidth / actualWidth;
            actualWidth *= f;
            actualHeight *= f;
        }

        return new LytRect(x, y, Math.round(actualWidth), Math.round(actualHeight));
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
    }

    @Override
    public void render(RenderContext context) {
        if (sprite != null) {
            context.fillIcon(getBounds(), sprite, color);
        }
    }
}
