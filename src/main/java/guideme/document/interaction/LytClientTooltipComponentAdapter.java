package guideme.document.interaction;

import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;

final class LytClientTooltipComponentAdapter extends LytBlock {
    private final ClientTooltipComponent component;

    public LytClientTooltipComponentAdapter(ClientTooltipComponent component) {
        this.component = component;
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return new LytRect(
                x, y, component.getWidth(Minecraft.getInstance().font), component.getHeight());
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
        component.renderText(
                Minecraft.getInstance().font,
                bounds.x(),
                bounds.y(),
                context.guiGraphics().pose().last().pose(),
                context.guiGraphics().bufferSource());
    }

    @Override
    public void render(RenderContext context) {
        component.renderImage(
                Minecraft.getInstance().font,
                bounds.x(),
                bounds.y(),
                context.guiGraphics());
    }
}
