package guideme.compiler;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstPosition;

public interface IndexingSink {
    default void appendText(MdAstNode node, String text) {
        appendText(node.position, text);
    }

    void appendText(MdAstPosition position, String text);

    void appendBreak();
}
