package guideme.internal.web;

import java.util.Locale;
import java.util.regex.Pattern;
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

    static String guiScaledDimension(Number value) {
        return "calc(" + value + "px * var(--gui-scale))";
    }

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#([0-9a-fA-F]{2}){3,4}$");

    /**
     * Converts a color in the format used by guide markup (#RRGGBB, #AARRGGBB or transparent) to CSS.
     *
     * @return null if the color is malformed
     */
    @Nullable
    static String toCssColor(String color) {
        if ("transparent".equals(color)) {
            return "transparent";
        }
        if (!COLOR_PATTERN.matcher(color).matches()) {
            return null;
        }
        if (color.length() == 7) {
            return color;
        }
        // CSS has alpha last
        return "#" + color.substring(3) + color.substring(1, 3);
    }

    static String toCssColor(int argb) {
        return String.format(Locale.ROOT, "#%06x%02x", argb & 0xFFFFFF, (argb >>> 24) & 0xFF);
    }
}
