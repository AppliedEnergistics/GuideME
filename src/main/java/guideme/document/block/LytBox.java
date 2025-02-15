package guideme.document.block;

import guideme.document.LytRect;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;

public abstract class LytBox extends LytBlock implements LytBlockContainer {
    protected final List<LytBlock> children = new ArrayList<>();

    protected int paddingLeft;
    protected int paddingTop;
    protected int paddingRight;
    protected int paddingBottom;

    private final BorderRenderer borderRenderer = new BorderRenderer();

    @Override
    public void removeChild(LytNode node) {
        if (node instanceof LytBlock block && block.parent == this) {
            children.remove(block);
            block.parent = null;
        }
    }

    @Override
    public void append(LytBlock block) {
        if (block.parent != null) {
            block.parent.removeChild(block);
        }
        block.parent = this;
        children.add(block);
    }

    public void clearContent() {
        for (var child : children) {
            child.parent = null;
        }
        children.clear();
    }

    protected abstract LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth);

    @Override
    protected final LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        int borderTop = getBorderTop().width();
        int borderLeft = getBorderLeft().width();
        int borderRight = getBorderRight().width();
        int borderBottom = getBorderBottom().width();

        // Apply padding and border
        var innerLayout = computeBoxLayout(
                context,
                x + paddingLeft + borderLeft,
                y + paddingTop + borderTop,
                availableWidth - paddingLeft - paddingRight - borderLeft - borderRight);

        return innerLayout.expand(
                paddingLeft + borderLeft,
                paddingTop + borderTop,
                paddingRight + borderRight,
                paddingBottom + borderBottom);
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
        for (var child : children) {
            child.setLayoutPos(child.bounds.point().add(deltaX, deltaY));
        }
    }

    public final void setPadding(int padding) {
        paddingLeft = padding;
        paddingTop = padding;
        paddingRight = padding;
        paddingBottom = padding;
    }

    @Override
    public List<? extends LytNode> getChildren() {
        return children;
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
        context.poseStack().pushPose();
        context.poseStack().translate(0, 0, 0.1);
        for (var child : children) {
            child.renderBatch(context, buffers);
        }
        context.poseStack().popPose();
    }

    @Override
    public void render(RenderContext context) {
        context.poseStack().pushPose();
        context.poseStack().translate(0, 0, 0.1);
        for (var child : children) {
            child.render(context);
        }

        context.poseStack().translate(0, 0, 0.1);
        // Render border on top of children
        borderRenderer.render(
                context,
                bounds,
                getBorderTop(),
                getBorderLeft(),
                getBorderRight(),
                getBorderBottom());

        context.poseStack().popPose();
    }
}
