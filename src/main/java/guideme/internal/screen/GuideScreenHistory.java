package guideme.internal.screen;

import guideme.PageAnchor;
import java.util.Optional;

public interface GuideScreenHistory {
    /**
     * Append to history if it's not already appended
     */
    void push(PageAnchor anchor);

    PageAnchor get(int index);

    Optional<PageAnchor> current();

    Optional<PageAnchor> forward();

    Optional<PageAnchor> peekForward();

    Optional<PageAnchor> back();

    Optional<PageAnchor> peekBack();
}
