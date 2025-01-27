package guideme.compiler;

import guideme.libs.unist.UnistNode;
import org.jetbrains.annotations.ApiStatus;

/**
 * Sink for indexing page content.
 */
@ApiStatus.NonExtendable
public interface IndexingSink {
    void appendText(UnistNode parent, String text);

    void appendBreak();
}
