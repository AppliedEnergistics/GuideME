package guideme.internal.web;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class HtmlUtils {
    static String escapeHtml(String text) {
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

    static String escapeAttribute(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final Set<String> VOID_ELEMENTS = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "source", "track", "wbr");

    static String createHtmlElement(String tag, Map<String, Object> attributes) {
        return createHtmlElement(tag, attributes, null);
    }

    static String createHtmlElement(String tag, Map<String, Object> attributes, @Nullable String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag);
        for (var entry : attributes.entrySet()) {
            sb.append(" ").append(entry.getKey()).append("=\"")
                    .append(escapeAttribute(String.valueOf(entry.getValue()))).append("\"");
        }
        if (VOID_ELEMENTS.contains(tag) && content == null) {
            sb.append("/>");
        } else {
            sb.append(">");
            if (content != null) {
                sb.append(content);
            }
            sb.append("</").append(tag).append(">");
        }
        return sb.toString();
    }

    static String guiScaledDimension(Number value) {
        return "calc(" + value + "px * var(--gui-scale))";
    }
}
