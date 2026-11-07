package guideme.compiler;

import guideme.Guide;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A link that points to a page in a different guide, as written in a Markdown page.
 * <p>
 * The URL scheme is {@code guide:}, followed by the id of the target guide, a {@code #} and the page within that guide.
 * An anchor within the page may be appended after another {@code #}:
 *
 * <pre>{@code
 * [Overview](guide:ae2:guide#getting-started.md)
 * [Section](guide:ae2:guide#getting-started.md#usage)
 * }</pre>
 *
 * The page may be given as a full page id ({@code ae2:getting-started.md}) or relative to the default namespace of the
 * target guide.
 *
 * @param guideId  The id of the guide the page belongs to.
 * @param pageText The page within the guide, as written in the link.
 * @param anchor   ID of an anchor within the page, if any.
 */
public record GuideLink(Identifier guideId, String pageText, @Nullable String anchor) {
    /**
     * URL scheme used to mark links that point into another guide.
     */
    public static final String SCHEME = "guide:";

    /**
     * {@return whether the given link URL is a cross-guide link}
     */
    public static boolean isGuideLink(@Nullable String href) {
        return href != null && href.startsWith(SCHEME);
    }

    /**
     * Parses a cross-guide link.
     *
     * @throws IllegalArgumentException If the link is malformed.
     */
    public static GuideLink parse(String href) {
        if (!isGuideLink(href)) {
            throw new IllegalArgumentException("Not a guide link: " + href);
        }

        var parts = href.substring(SCHEME.length()).split("#", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Expected guide:<guide id>#<page>, but got: " + href);
        }

        // Identifier.parse assumes the minecraft namespace for ids without one, but a guide id is only usable if it
        // names its namespace explicitly
        if (!parts[0].contains(":")) {
            throw new IllegalArgumentException("Invalid guide id, expected <namespace>:<path>: " + parts[0]);
        }

        Identifier guideId;
        try {
            guideId = Identifier.parse(parts[0]);
        } catch (IdentifierException e) {
            throw new IllegalArgumentException("Invalid guide id: " + parts[0]);
        }

        var pageText = parts[1];
        if (pageText.isBlank()) {
            throw new IllegalArgumentException("Missing page id in guide link: " + href);
        }

        String anchor = null;
        if (parts.length > 2 && !parts[2].isBlank()) {
            anchor = parts[2];
        }

        return new GuideLink(guideId, pageText, anchor);
    }

    /**
     * Resolves the page id against the default namespace of the target guide, unless the page was given as a full page
     * id.
     */
    public Identifier resolvePageId(Guide guide) {
        return IdUtils.resolveId(pageText, guide.getDefaultNamespace());
    }
}
