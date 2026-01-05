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

    public static String escapeAttribute(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String guiScaledDimension(Number value) {
        return "calc(" + value + "px * var(--gui-scale))";
    }
}
