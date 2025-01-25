package guideme.document.flow;

import guideme.document.block.LytVisitor;
import java.util.Objects;

public class LytFlowText extends LytFlowContent {
    private String text = "";

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public static LytFlowText of(String text) {
        var node = new LytFlowText();
        node.setText(text);
        return node;
    }

    @Override
    protected void visitChildren(LytVisitor visitor) {
        visitor.text(text);
    }
}
