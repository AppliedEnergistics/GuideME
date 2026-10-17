package guideme.web.html;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A node in a minimal HTML DOM, used to generate the pages of the website. Use the static factory methods to create
 * nodes.
 */
public sealed abstract class HtmlNode permits HtmlTag, HtmlText {

    /**
     * {@return the name of the HTML tag or an empty string for text nodes}
     */
    public abstract String name();

    /**
     * {@return the value of the attribute, or null if it isn't set or has no value}
     */
    @Nullable
    public abstract String attribute(String name);

    public final boolean hasAttribute(String name) {
        return attribute(name) != null;
    }

    /**
     * Sets an attribute. A null value writes the attribute without a value (i.e. {@code <details open>}).
     *
     * @throws UnsupportedOperationException For text nodes.
     */
    public abstract HtmlTag setAttribute(String name, String value);

    public final HtmlTag setAttribute(String name, float value) {
        return setAttribute(name, "" + value);
    }

    public final HtmlTag setAttribute(String name, int value) {
        return setAttribute(name, "" + value);
    }

    /**
     * Creates an element.
     *
     * @throws IllegalArgumentException If the name is not a valid tag name.
     */
    public static HtmlTag tag(String name) {
        return new HtmlTag(name);
    }

    public static HtmlTag tag(String name, HtmlFragment fragment) {
        var tag = tag(name);
        tag.append(fragment);
        return tag;
    }

    public static HtmlTag tag(String name, HtmlNode... children) {
        return tag(name, Arrays.asList(children));
    }

    public static HtmlTag tag(String name, List<? extends HtmlNode> children) {
        var tag = tag(name);
        for (var node : children) {
            tag.append(node);
        }
        return tag;
    }

    /**
     * Creates a text node, whose content is escaped when written.
     */
    public static HtmlText text(String content) {
        return new HtmlText(content);
    }

    public abstract List<HtmlNode> children();

    /**
     * Escapes text for use in element content. Null becomes an empty string.
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Escapes text for use in a quoted attribute value. Null becomes an empty string.
     */
    public static String escapeAttribute(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * {@return the text of this node and its descendants, without markup}
     */
    public abstract String textContent();

    /**
     * {@return the HTML of this node and its descendants}
     */
    public abstract String outerHtml();
}
