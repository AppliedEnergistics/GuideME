package guideme.guidebook.document.flow;

import guideme.api.style.Styleable;
import guideme.api.style.TextStyle;
import guideme.guidebook.document.block.LytVisitor;
import org.jetbrains.annotations.Nullable;

public class LytFlowContent implements Styleable {
    private TextStyle style = TextStyle.EMPTY;
    private TextStyle hoverStyle = TextStyle.EMPTY;

    private LytFlowParent parent;

    public LytFlowParent getParent() {
        return parent;
    }

    public void setParent(LytFlowParent parent) {
        this.parent = parent;
    }

    /**
     * Gets the parent of this flow content that is itself flow content. Null if the parent is null or not flow content.
     */
    @Nullable
    public LytFlowContent getFlowParent() {
        return parent instanceof LytFlowContent flowContent ? flowContent : null;
    }

    public boolean isInclusiveAncestor(LytFlowContent flowContent) {
        for (var content = flowContent; content != null; content = content.getFlowParent()) {
            if (content == this) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TextStyle getStyle() {
        return style;
    }

    @Override
    public void setStyle(TextStyle style) {
        this.style = style;
    }

    @Override
    public TextStyle getHoverStyle() {
        return hoverStyle;
    }

    @Override
    public void setHoverStyle(TextStyle style) {
        this.hoverStyle = style;
    }

    @Override
    public @Nullable Styleable getStylingParent() {
        return getParent() instanceof Styleable stylingParent ? stylingParent : null;
    }

    public final void visit(LytVisitor visitor) {
        visitor.beforeFlowContent(this);
        visitChildren(visitor);
        visitor.afterFlowContent(this);
    }

    protected void visitChildren(LytVisitor visitor) {
    }
}
