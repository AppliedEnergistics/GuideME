package guideme.internal.screen;

import guideme.render.RenderContext;
import guideme.render.SimpleRenderContext;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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

        scaledGraphics.renderDeferredElements();

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
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        event = toVirtual(event);
        return scaledMouseClicked(event, doubleClick);
    }

    protected boolean scaledMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        event = toVirtual(event);
        return scaledMouseReleased(event);
    }

    protected boolean scaledMouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return scaledMouseDragged(toVirtual(event), toVirtual(dragX), toVirtual(dragY));
    }

    protected boolean scaledMouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event, dragX, dragY);
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

    protected final MouseButtonEvent toVirtual(MouseButtonEvent event) {
        return new MouseButtonEvent(
                toVirtual(event.x()),
                toVirtual(event.y()),
                event.buttonInfo());
    }

    protected final double toVirtual(double value) {
        return value / effectiveScale;
    }

    public double getEffectiveScale() {
        return effectiveScale;
    }
}
