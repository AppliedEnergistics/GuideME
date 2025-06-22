package guideme.document.block;

import guideme.document.LytRect;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import guideme.style.BorderStyle;
import org.joml.Vector2i;

public abstract class LytBlock extends LytNode {
    /**
     * Content rectangle.
     */
    protected LytRect bounds = LytRect.empty();

    private int marginTop;
    private int marginLeft;
    private int marginRight;
    private int marginBottom;

    private BorderStyle borderTop = BorderStyle.NONE;
    private BorderStyle borderLeft = BorderStyle.NONE;
    private BorderStyle borderRight = BorderStyle.NONE;
    private BorderStyle borderBottom = BorderStyle.NONE;

    /**
     * Always expand this block to the full available width.
     */
    private boolean fullWidth;

    @Override
    public LytRect getBounds() {
        return bounds;
    }

    public boolean isCulled(LytRect viewport) {
        return !viewport.intersects(bounds);
    }

    public final void setLayoutPos(Vector2i point) {
        var deltaX = point.x - bounds.x();
        var deltaY = point.y - bounds.y();
        if (deltaX != 0 || deltaY != 0) {
            bounds = bounds.withX(point.x).withY(point.y);
            onLayoutMoved(deltaX, deltaY);
        }
    }

    public final LytRect layout(LayoutContext context, int x, int y, int availableWidth) {
        bounds = computeLayout(context, x, y, availableWidth);
        if (fullWidth && bounds.width() < availableWidth) {
            bounds = bounds.withWidth(availableWidth);
        }
        return bounds;
    }

    public int getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(int marginTop) {
        this.marginTop = marginTop;
    }

    public int getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(int marginLeft) {
        this.marginLeft = marginLeft;
    }

    public int getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(int marginRight) {
        this.marginRight = marginRight;
    }

    public int getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(int marginBottom) {
        this.marginBottom = marginBottom;
    }

    public int getMarginStart(LytAxis axis) {
        return switch (axis) {
            case HORIZONTAL -> getMarginLeft();
            case VERTICAL -> getMarginTop();
        };
    }

    public int getMarginEnd(LytAxis axis) {
        return switch (axis) {
            case HORIZONTAL -> getMarginRight();
            case VERTICAL -> getMarginBottom();
        };
    }

    public BorderStyle getBorderTop() {
        return borderTop;
    }

    public void setBorderTop(BorderStyle borderTop) {
        this.borderTop = borderTop;
    }

    public BorderStyle getBorderLeft() {
        return borderLeft;
    }

    public void setBorderLeft(BorderStyle borderLeft) {
        this.borderLeft = borderLeft;
    }

    public BorderStyle getBorderRight() {
        return borderRight;
    }

    public void setBorderRight(BorderStyle borderRight) {
        this.borderRight = borderRight;
    }

    public BorderStyle getBorderBottom() {
        return borderBottom;
    }

    public void setBorderBottom(BorderStyle borderBottom) {
        this.borderBottom = borderBottom;
    }

    public void setBorder(BorderStyle style) {
        setBorderTop(style);
        setBorderLeft(style);
        setBorderRight(style);
        setBorderBottom(style);
    }

    public boolean isFullWidth() {
        return fullWidth;
    }

    public void setFullWidth(boolean fullWidth) {
        this.fullWidth = fullWidth;
    }

    protected abstract LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth);

    /**
     * Implement to react to layout previously computed by {@link #computeLayout} being moved.
     */
    protected abstract void onLayoutMoved(int deltaX, int deltaY);

    public abstract void render(RenderContext context);
}
