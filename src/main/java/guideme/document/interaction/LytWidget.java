package guideme.document.interaction;

import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.internal.screen.IndepentScaleScreen;
import guideme.internal.screen.ScaledGuiGraphics;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import guideme.ui.GuideUiHost;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Wraps an {@link AbstractWidget} for use within the guidebook layout tree.
 */
public class LytWidget extends LytBlock implements InteractiveElement {
    private final AbstractWidget widget;

    public LytWidget(AbstractWidget widget) {
        this.widget = widget;
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return new LytRect(
                x, y,
                widget.getWidth(), widget.getHeight());
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
        widget.setX(widget.getX() + deltaX);
        widget.setY(widget.getY() + deltaY);
    }

    @Override
    public void render(RenderContext context) {
        updateWidgetPosition();

        var minecraft = Minecraft.getInstance();

        if (!(minecraft.screen instanceof GuideUiHost uiHost)) {
            return; // Can't render if we can't translate
        }

        var mouseHandler = minecraft.mouseHandler;
        // We use screen here so it accounts for our gui-scale independent scaling screen.
        var xScale = (double) minecraft.screen.width / (double) minecraft.getWindow().getScreenWidth();
        var yScale = (double) minecraft.screen.height / (double) minecraft.getWindow().getScreenHeight();
        var mouseX = mouseHandler.xpos() * xScale;
        var mouseY = mouseHandler.ypos() * yScale;

        var mouseDocPos = uiHost.getDocumentPoint(mouseX, mouseY);

        // This is a bit of a hack, but since scissor checks break out of the scaled environment,
        // we pass the scaled gui graphics to the widget to fix calls to containsPointInScissor
        GuiGraphics guiGraphics = context.guiGraphics();
        if (minecraft.screen instanceof IndepentScaleScreen indepentScaleScreen) {
            guiGraphics = new ScaledGuiGraphics(minecraft, context.guiGraphics().pose(),
                    context.guiGraphics().guiRenderState, (float) indepentScaleScreen.getEffectiveScale());
        }
        widget.render(
                guiGraphics,
                mouseDocPos != null ? mouseDocPos.x() : -100,
                mouseDocPos != null ? mouseDocPos.y() : -100,
                // Using real-time here since the game may pause in the background
                minecraft.getDeltaTracker().getRealtimeDeltaTicks());
    }

    private void updateWidgetPosition() {
        widget.setPosition(bounds.x(), bounds.y());
    }

    @Override
    public boolean mouseMoved(GuideUiHost screen, int x, int y) {
        widget.mouseMoved(x, y);
        return true;
    }

    @Override
    public boolean mouseClicked(GuideUiHost screen, int x, int y, int button) {
        return widget.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(GuideUiHost screen, int x, int y, int button) {
        return widget.mouseReleased(x, y, button);
    }

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        return Optional.empty();
    }

    public AbstractWidget getWidget() {
        return widget;
    }
}
