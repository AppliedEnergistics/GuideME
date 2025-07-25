package guideme.layout.flow;

import guideme.document.LytRect;
import guideme.document.flow.LytFlowContent;
import guideme.render.RenderContext;
import org.jetbrains.annotations.Nullable;

public abstract class LineElement {
    /**
     * Next Element in flow direction.
     */
    @Nullable
    LineElement next;

    LytRect bounds = LytRect.empty();

    /**
     * The original flow content this line element is associated with.
     */
    @Nullable
    LytFlowContent flowContent;

    boolean containsMouse;

    boolean floating;

    @Nullable
    public LytFlowContent getFlowContent() {
        return flowContent;
    }

    /**
     * Render any other content individually.
     */
    public void render(RenderContext context) {
    }
}
