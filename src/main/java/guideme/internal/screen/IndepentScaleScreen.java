package guideme.internal.screen;

import guideme.render.RenderContext;
import guideme.render.SimpleRenderContext;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class IndepentScaleScreen extends Screen {

    /**
     * The scale vs. the current gui scale to reach the desired scaling.
     */
    private double effectiveScale;

    protected IndepentScaleScreen(Component title) {
        super(title);
        this.effectiveScale = calculateEffectiveScale();
    }

    protected abstract float calculateEffectiveScale();

    @Override
    protected void init() {
        super.init();

        this.width = toVirtual(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        this.height = toVirtual(Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    @Override
    public final void resize(Minecraft minecraft, int width, int height) {
        this.effectiveScale = calculateEffectiveScale();
        super.resize(minecraft, toVirtual(width), toVirtual(height));
    }

    @Override
    public final void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var scaledGraphics = new ScaledGuiGraphics(
                Minecraft.getInstance(),
                guiGraphics.pose(),
                guiGraphics.guiRenderState,
                (float) this.effectiveScale);

        var renderContext = new SimpleRenderContext(guiGraphics);

        scaledGraphics.pose().pushMatrix();
        // This scale has to be uniform, otherwise items rendered with it will have messed up normals (and broken
        // lighting)
        scaledGraphics.pose().scale((float) effectiveScale, (float) effectiveScale);
        scaledRender(scaledGraphics, renderContext, toVirtual(mouseX), toVirtual(mouseY), partialTick);

        // Move this here to render the tooltip with scaling applied
        // TODO 1.21.6 if (this.deferredTooltipRendering != null) {
        // TODO 1.21.6 scaledGraphics.renderTooltip(this.font, this.deferredTooltipRendering.tooltip(),
        // TODO 1.21.6 this.deferredTooltipRendering.positioner(), toVirtual(mouseX), toVirtual(mouseY));
        // TODO 1.21.6 this.deferredTooltipRendering = null;
        // TODO 1.21.6 }

        scaledGraphics.pose().popMatrix();
    }

    protected void scaledRender(GuiGraphics guiGraphics, RenderContext context, int mouseX, int mouseY,
            float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        return super.getChildAt(toVirtual(mouseX), toVirtual(mouseY));
    }

    protected final Optional<GuiEventListener> getScaledChildAt(double mouseX, double mouseY) {
        return super.getChildAt(mouseX, mouseY);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return scaledMouseClicked(toVirtual(mouseX), toVirtual(mouseY), button);
    }

    protected boolean scaledMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scaledMouseReleased(toVirtual(mouseX), toVirtual(mouseY), button);
    }

    protected boolean scaledMouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scaledMouseDragged(toVirtual(mouseX), toVirtual(mouseY), button, dragX, dragY);
    }

    protected boolean scaledMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scaledMouseScrolled(toVirtual(mouseX), toVirtual(mouseY), scrollX, scrollY);
    }

    protected boolean scaledMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public final void mouseMoved(double mouseX, double mouseY) {
        scaledMouseMoved(toVirtual(mouseX), toVirtual(mouseY));
    }

    protected void scaledMouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
    }

    protected final int toVirtual(int value) {
        return (int) Math.round(value / effectiveScale);
    }

    protected final double toVirtual(double value) {
        return value / effectiveScale;
    }

    public double getEffectiveScale() {
        return effectiveScale;
    }
}
