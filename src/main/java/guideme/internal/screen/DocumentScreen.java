package guideme.internal.screen;

import com.mojang.blaze3d.platform.Window;
import guideme.color.ColorValue;
import guideme.color.ConstantColor;
import guideme.document.DefaultStyles;
import guideme.document.LytPoint;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.block.LytDocument;
import guideme.document.block.LytNode;
import guideme.document.flow.LytFlowContainer;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.internal.GuideMEClient;
import guideme.internal.util.DashPattern;
import guideme.internal.util.DashedRectangle;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.render.RenderContext;
import guideme.ui.GuideUiHost;
import guideme.ui.UiPoint;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DocumentScreen extends IndepentScaleScreen implements GuideUiHost {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentScreen.class);

    private static final DashPattern DEBUG_NODE_OUTLINE = new DashPattern(1f, 4, 3, 0xFFFFFFFF, 500);
    private static final DashPattern DEBUG_CONTENT_OUTLINE = new DashPattern(0.5f, 2, 1, 0x7FFFFFFF, 500);
    private static final ColorValue DEBUG_HOVER_OUTLINE_COLOR = new ConstantColor(0x7FFFFF00);

    // 20 virtual px margin around the document
    private static final int FULL_SCREEN_MARGIN = 20;

    @Nullable
    private InteractiveElement mouseCaptureTarget;

    private LytDocument lastDocument;

    private final GuideScrollbar scrollbar;

    protected LytRect screenRect = LytRect.empty();

    public DocumentScreen(Component title) {
        super(title);
        this.scrollbar = new GuideScrollbar();
    }

    @Override
    protected void init() {
        super.init();

        if (isFullWidthLayout()) {
            screenRect = new LytRect(0, 0, width, height);
        } else {
            var screenWidth = Math.min(getMaxColumnWidth(), width);
            var left = (width - screenWidth) / 2;
            screenRect = new LytRect(left, 0, screenWidth, height);
        }

        // Add and re-position scrollbar
        addRenderableWidget(scrollbar);
        updateDocumentLayout();
    }

    protected int getMaxColumnWidth() {
        return 400;
    }

    @Override
    protected float calculateEffectiveScale() {
        if (!GuideMEClient.instance().isAdaptiveScalingEnabled()) {
            return 1f;
        }

        // The unifont is already scaled down by half at gui scale 1
        // and at scale 3 it is scaled to 150%, both look bad
        // For GUI scales 1 and 3 scale up the entire screen by 1
        var window = Minecraft.getInstance().getWindow();
        var currentScale = window.getGuiScale();
        var effectiveScale = currentScale;
        if (currentScale == 1) {
            effectiveScale = 2;
        } else if (currentScale == 3) {
            effectiveScale = 4;
        }

        // Validate that when we scale up, we still are above the base width/height
        var virtualWidth = window.getWidth() / effectiveScale;
        var virtualHeight = window.getHeight() / effectiveScale;
        if (virtualWidth < Window.BASE_WIDTH || virtualHeight < Window.BASE_HEIGHT) {
            var reducedEffectiveScale = Math.max(2, currentScale - 1);
            LOG.debug("Not enough screen space ({}x{}) to increase GUI scale from {} to {}. Decreasing to {} instead.",
                    virtualWidth, virtualHeight, currentScale, effectiveScale, reducedEffectiveScale);
            effectiveScale = reducedEffectiveScale;
        }

        return (float) (effectiveScale / currentScale);
    }

    protected void updateDocumentLayout() {

        var docViewport = getDocumentViewport();
        var context = new LayoutContext(new MinecraftFontMetrics());

        // Build layout if needed
        var document = getDocument();
        if (document != null) {
            document.updateLayout(context, docViewport.width());
            scrollbar.setContentHeight(document.getContentHeight());
        }

        var docRect = getDocumentRect();
        scrollbar.move(docRect.right(), docRect.y(), docRect.height());
    }

    @Nullable
    protected abstract LytDocument getDocument();

    @Override
    public LytRect getDocumentRect() {
        return new LytRect(
                screenRect.x() + getMarginLeft(),
                screenRect.y() + getMarginTop(),
                screenRect.width() - getMarginLeft() - getMarginRight(),
                screenRect.height() - getMarginBottom() - getMarginTop());
    }

    @Override
    public final LytRect getDocumentViewport() {
        var documentRect = getDocumentRect();
        return new LytRect(0, scrollbar.getScrollAmount(), documentRect.width(), documentRect.height());
    }

    @Override
    public void tick() {
        super.tick();

        // Tick all controls on the page
        var document = getDocument();
        if (lastDocument != document) {
            releaseMouseCapture();
            lastDocument = document;
        }
        if (document != null) {
            tickNode(document);
        }
    }

    private static void tickNode(LytNode node) {
        node.tick();

        for (var child : node.getChildren()) {
            tickNode(child);
        }
    }

    protected final void setDocumentScrollY(int scrollY) {
        scrollbar.setScrollAmount(scrollY);
    }

    protected final void renderDocument(RenderContext context) {

        // Set scissor rectangle to rect that we show the document in
        var documentRect = getDocumentRect();

        var document = getDocument();
        if (document == null) {
            return;
        }

        // Move rendering to anchor @ 0,0 in the document rect
        var documentViewport = getDocumentViewport();
        var poseStack = context.poseStack();

        // guiGraphics.enableScissor(documentRect.x(), documentRect.y(), documentRect.right(), documentRect.bottom());
        context.pushScissor(documentRect);
        poseStack.pushPose();
        poseStack.translate(documentRect.x() - documentViewport.x(), documentRect.y() - documentViewport.y(), 0);

        // Render all text content in one large batch to improve performance
        var buffers = context.beginBatch();
        document.renderBatch(context, buffers);
        context.endBatch(buffers);

        document.render(context);

        context.popScissor();

        if (GuideMEClient.instance().isShowDebugGuiOverlays()) {
            renderHoverOutline(document, context);
        }

        poseStack.popPose();

    }

    private static void renderHoverOutline(LytDocument document, RenderContext context) {
        var hoveredElement = document.getHoveredElement();
        if (hoveredElement != null) {
            // Fill a rectangle highlighting margins
            if (hoveredElement.node() instanceof LytBlock block) {
                var bounds = block.getBounds();
                if (block.getMarginTop() > 0) {
                    context.fillRect(
                            bounds.withHeight(block.getMarginTop()).move(0, -block.getMarginTop()),
                            DEBUG_HOVER_OUTLINE_COLOR);
                }
                if (block.getMarginBottom() > 0) {
                    context.fillRect(
                            bounds.withHeight(block.getMarginBottom()).move(0, bounds.height()),
                            DEBUG_HOVER_OUTLINE_COLOR);
                }
                if (block.getMarginLeft() > 0) {
                    context.fillRect(
                            bounds.withWidth(block.getMarginLeft()).move(-block.getMarginLeft(), 0),
                            DEBUG_HOVER_OUTLINE_COLOR);
                }
                if (block.getMarginRight() > 0) {
                    context.fillRect(
                            bounds.withWidth(block.getMarginRight()).move(bounds.width(), 0),
                            DEBUG_HOVER_OUTLINE_COLOR);
                }
            }

            // Fill the content rectangle
            DashedRectangle.render(context.poseStack(), hoveredElement.node().getBounds(), DEBUG_NODE_OUTLINE, 0);

            // Also outline any inline-elements in the block
            if (hoveredElement.content() != null) {
                if (hoveredElement.node() instanceof LytFlowContainer flowContainer) {
                    flowContainer.enumerateContentBounds(hoveredElement.content())
                            .forEach(bound -> {
                                DashedRectangle.render(context.poseStack(), bound, DEBUG_CONTENT_OUTLINE, 0);
                            });
                }
            }

            // Render the class-name of the hovered node to make it easier to identify
            var bounds = hoveredElement.node().getBounds();
            context.renderText(
                    hoveredElement.node().getClass().getName(),
                    DefaultStyles.BASE_STYLE,
                    bounds.x(),
                    bounds.bottom());
        }
    }

    @Override
    public void scaledMouseMoved(double mouseX, double mouseY) {
        super.scaledMouseMoved(mouseX, mouseY);

        if (mouseCaptureTarget != null) {
            var docPointUnclamped = getDocumentPointUnclamped(mouseX, mouseY);
            mouseCaptureTarget.mouseMoved(this, docPointUnclamped.x(), docPointUnclamped.y());
        }

        var docPoint = getDocumentPoint(mouseX, mouseY);
        if (docPoint != null) {
            dispatchEvent(docPoint.x(), docPoint.y(), el -> {
                return el.mouseMoved(this, docPoint.x(), docPoint.y());
            });
        }
    }

    @Override
    public boolean scaledMouseClicked(double mouseX, double mouseY, int button) {
        if (super.scaledMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        var docPoint = getDocumentPoint(mouseX, mouseY);
        if (docPoint != null) {
            if (documentClicked(docPoint, button)) {
                return true;
            }

            return dispatchEvent(docPoint.x(), docPoint.y(), el -> {
                return el.mouseClicked(this, docPoint.x(), docPoint.y(), button);
            });
        } else {
            return false;
        }
    }

    protected boolean documentClicked(UiPoint documentPoint, int button) {
        return false;
    }

    @Override
    public boolean scaledMouseReleased(double mouseX, double mouseY, int button) {
        if (super.scaledMouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        if (mouseCaptureTarget != null) {
            var currentTarget = mouseCaptureTarget;

            var docPointUnclamped = getDocumentPointUnclamped(mouseX, mouseY);
            currentTarget.mouseReleased(this, docPointUnclamped.x(), docPointUnclamped.y(), button);

            releaseMouseCapture(currentTarget);
        }

        var docPoint = getDocumentPoint(mouseX, mouseY);
        if (docPoint != null) {
            return dispatchEvent(docPoint.x(), docPoint.y(), el -> {
                return el.mouseReleased(this, docPoint.x(), docPoint.y(), button);
            });
        } else {
            return false;
        }
    }

    @FunctionalInterface
    interface EventInvoker {
        boolean invoke(InteractiveElement el);
    }

    private boolean dispatchEvent(int x, int y, EventInvoker invoker) {
        return dispatchInteraction(x, y, el -> {
            if (invoker.invoke(el)) {
                return Optional.of(true);
            } else {
                return Optional.empty();
            }
        }).orElse(false);
    }

    private <T> Optional<T> dispatchInteraction(int x, int y, Function<InteractiveElement, Optional<T>> invoker) {
        var document = getDocument();
        if (document != null) {
            var underCursor = document.pick(x, y);
            if (underCursor != null) {
                return dispatchInteraction(underCursor, invoker);
            }
        }

        return Optional.empty();
    }

    private static <T> Optional<T> dispatchInteraction(LytDocument.HitTestResult receiver,
            Function<InteractiveElement, Optional<T>> invoker) {
        // Iterate through content ancestors
        for (var el = receiver.content(); el != null; el = el.getFlowParent()) {
            if (el instanceof InteractiveElement interactiveEl) {
                var result = invoker.apply(interactiveEl);
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        // Iterate through node ancestors
        for (var node = receiver.node(); node != null; node = node.getParent()) {
            if (node instanceof InteractiveElement interactiveEl) {
                var result = invoker.apply(interactiveEl);
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public void afterMouseMove() {
        super.afterMouseMove();

        var document = getDocument();
        if (document != null) {
            var mouseHandler = minecraft.mouseHandler;
            // We use screen here so it accounts for our gui-scale independent scaling screen.
            var xScale = (double) minecraft.screen.width / (double) minecraft.getWindow().getScreenWidth();
            var yScale = (double) minecraft.screen.height / (double) minecraft.getWindow().getScreenHeight();
            var x = mouseHandler.xpos() * xScale;
            var y = mouseHandler.ypos() * yScale;

            // If there's a widget under the cursor, ignore document hit-testing
            if (getScaledChildAt(x, y).isPresent()) {
                document.setHoveredElement(null);
                return;
            }

            var docPoint = getDocumentPoint(x, y);
            if (docPoint != null) {
                var hoveredEl = document.pick(docPoint.x(), docPoint.y());
                document.setHoveredElement(hoveredEl);
            } else {
                document.setHoveredElement(null);
            }
        }
    }

    @Override
    public @Nullable UiPoint getDocumentPoint(double screenX, double screenY) {
        var documentRect = getDocumentRect();

        if (screenX >= documentRect.x() && screenX < documentRect.right()
                && screenY >= documentRect.y() && screenY < documentRect.bottom()) {
            return getDocumentPointUnclamped(screenX, screenY);
        }

        return null; // Outside the document
    }

    @Override
    public UiPoint getDocumentPointUnclamped(double screenX, double screenY) {
        var documentRect = getDocumentRect();
        var docX = (int) Math.round(screenX - documentRect.x());
        var docY = (int) Math.round(screenY + scrollbar.getScrollAmount() - documentRect.y());
        return new UiPoint(docX, docY);
    }

    /**
     * Translate a point from within the document into the screen coordinate system.
     */
    @Override
    public LytPoint getScreenPoint(LytPoint documentPoint) {
        var documentRect = getDocumentRect();
        var documentViewport = getDocumentViewport();
        var x = documentPoint.x() - documentViewport.x();
        var y = documentPoint.y() - documentViewport.y();
        return new LytPoint(
                documentRect.x() + x,
                documentRect.y() + y);
    }

    @Override
    public boolean scaledMouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!super.scaledMouseScrolled(mouseX, mouseY, deltaX, deltaY)) {
            return scrollbar.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        return true;
    }

    protected final void renderDocumentTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var document = getDocument();
        // Render tooltip
        if (document != null && document.getHoveredElement() != null) {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        var docPos = getDocumentPoint(x, y);
        if (docPos == null) {
            return;
        }
        var hoveredElement = getDocument().getHoveredElement();
        if (hoveredElement != null) {
            dispatchInteraction(
                    hoveredElement,
                    el -> el.getTooltip(docPos.x(), docPos.y()))
                    .ifPresent(tooltip -> renderTooltip(guiGraphics, tooltip, x, y));
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, GuideTooltip tooltip, int mouseX, int mouseY) {
        var minecraft = Minecraft.getInstance();
        var clientLines = tooltip.getLines();

        if (clientLines.isEmpty()) {
            return;
        }

        int frameWidth = 0;
        int frameHeight = clientLines.size() == 1 ? -2 : 0;

        for (var clientTooltipComponent : clientLines) {
            frameWidth = Math.max(frameWidth, clientTooltipComponent.getWidth(minecraft.font));
            frameHeight += clientTooltipComponent.getHeight();
        }

        if (!tooltip.getIcon().isEmpty()) {
            frameWidth += 18;
            frameHeight = Math.max(frameHeight, 18);
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + frameWidth > this.width) {
            x -= 28 + frameWidth;
        }

        if (y + frameHeight + 6 > this.height) {
            y = this.height - frameHeight - 6;
        }

        int zOffset = 400;

        TooltipRenderUtil.renderTooltipBackground(guiGraphics, x, y, frameWidth, frameHeight, zOffset);

        if (!tooltip.getIcon().isEmpty()) {
            x += 18;
        }

        var poseStack = guiGraphics.pose();
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, zOffset);
        int currentY = y;

        // Batch-render tooltip text first
        for (int i = 0; i < clientLines.size(); ++i) {
            var line = clientLines.get(i);
            line.renderText(minecraft.font, x, currentY, poseStack.last().pose(), bufferSource);
            currentY += line.getHeight() + (i == 0 ? 2 : 0);
        }

        bufferSource.endBatch();

        // Then render tooltip decorations, items, etc.
        currentY = y;
        if (!tooltip.getIcon().isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 0, zOffset);
            guiGraphics.renderItem(tooltip.getIcon(), x - 18, y);
            poseStack.popPose();
        }

        for (int i = 0; i < clientLines.size(); ++i) {
            var line = clientLines.get(i);
            line.renderImage(minecraft.font, x, currentY, guiGraphics);
            currentY += line.getHeight() + (i == 0 ? 2 : 0);
        }
        poseStack.popPose();
    }

    @Override
    public @Nullable InteractiveElement getMouseCaptureTarget() {
        return mouseCaptureTarget;
    }

    @Override
    public void captureMouse(InteractiveElement element) {
        if (mouseCaptureTarget != element) {
            if (mouseCaptureTarget != null) {
                releaseMouseCapture(mouseCaptureTarget);
            }
            mouseCaptureTarget = element;
        }
    }

    @Override
    public void releaseMouseCapture(InteractiveElement element) {
        if (mouseCaptureTarget == element) {
            mouseCaptureTarget = null;
            element.mouseCaptureLost();
            if (mouseCaptureTarget != null) {
                throw new IllegalStateException("Element " + element + " recaptured the mouse in its release event");
            }
        }
    }

    private void releaseMouseCapture() {
        if (mouseCaptureTarget != null) {
            releaseMouseCapture(mouseCaptureTarget);
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        releaseMouseCapture();
    }

    protected int getMarginRight() {
        return isFullWidthLayout() ? FULL_SCREEN_MARGIN : 8;
    }

    protected int getMarginLeft() {
        return isFullWidthLayout() ? FULL_SCREEN_MARGIN : 0;
    }

    protected int getMarginTop() {
        return 20;
    }

    protected int getMarginBottom() {
        return hasFooter() ? FULL_SCREEN_MARGIN : 0;
    }

    protected boolean isFullWidthLayout() {
        return GuideMEClient.instance().isFullWidthLayout();
    }

    protected boolean hasFooter() {
        return false;
    }
}
