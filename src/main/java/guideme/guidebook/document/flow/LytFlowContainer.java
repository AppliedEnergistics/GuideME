package guideme.guidebook.document.flow;

import guideme.guidebook.document.LytRect;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public interface LytFlowContainer extends LytFlowParent {
    /**
     * Gets a stream of all the bounding rectangles for given flow content. Since flow content may be wrapped, it may
     * consist of several disjointed bounding boxes.
     */
    Stream<LytRect> enumerateContentBounds(LytFlowContent content);

    @Nullable
    LytFlowContent pickContent(int x, int y);
}
