package guideme.web.html;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of sibling nodes without a parent element.
 */
public final class HtmlFragment {
    private final List<HtmlNode> nodes = new ArrayList<>();

    public HtmlFragment() {
    }

    public HtmlFragment(HtmlNode... nodes) {
        Collections.addAll(this.nodes, nodes);
    }

    public List<HtmlNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public void append(HtmlNode node) {
        nodes.add(node);
    }

    /**
     * Appends text, which is escaped when written.
     */
    public void append(String text) {
        nodes.add(HtmlNode.text(text));
    }

    public void append(HtmlFragment fragment) {
        nodes.addAll(fragment.nodes);
    }

    /**
     * {@return the HTML of all nodes}
     */
    public String outerHtml() {
        var result = new StringBuilder();
        for (var node : nodes) {
            result.append(node.outerHtml());
        }
        return result.toString();
    }
}
