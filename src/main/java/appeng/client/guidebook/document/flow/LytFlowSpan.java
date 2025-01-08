package appeng.client.guidebook.document.flow;

import appeng.client.guidebook.document.block.LytVisitor;
import appeng.client.guidebook.style.Styleable;
import java.util.ArrayList;
import java.util.List;

/**
 * Attaches properties to a span of {@link LytFlowContent}, such as links or formatting.
 */
public class LytFlowSpan extends LytFlowContent implements LytFlowParent, Styleable {
    private final List<LytFlowContent> children = new ArrayList<>();

    public List<LytFlowContent> getChildren() {
        return children;
    }

    public void append(LytFlowContent child) {
        if (child.getParent() != null) {
            throw new IllegalStateException("Child is already owned by other span");
        }
        child.setParent(this);
        children.add(child);
    }

    @Override
    protected void visitChildren(LytVisitor visitor) {
        for (var child : children) {
            child.visit(visitor);
        }
    }
}
