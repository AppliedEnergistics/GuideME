package guideme.web;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed abstract class HtmlNode permits HtmlTag, HtmlText {

    /**
     * {@return the name of the HTML tag or an empty string for text nodes}
     */
    public abstract String name();

    @Nullable
    public abstract String attribute(String name);

    public final boolean hasAttribute(String name) {
        return attribute(name) != null;
    }

    public abstract HtmlTag setAttribute(String name, String value);

    public final HtmlTag setAttribute(String name, float value) {
        return setAttribute(name, "" + value);
    }

    public final HtmlTag setAttribute(String name, int value) {
        return setAttribute(name, "" + value);
    }

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

    public static HtmlText text(String content) {
        return new HtmlText(content);
    }

    public abstract List<HtmlNode> children();

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

    public static String escapeAttribute(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public abstract String textContent();

    public abstract String outerHtml();
}
