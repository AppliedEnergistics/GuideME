package guideme.internal.web;

import guideme.web.html.HtmlFragment;
import org.jspecify.annotations.Nullable;

/**
 * Placeholders of a page at the root of a website with multiple guide versions.
 *
 * @param pageTitle     The heading of the page as plain text.
 * @param pageTitleText The title of the page shown by the browser as plain text.
 * @param description   A summary of the page for search engines as plain text, if any.
 * @param canonicalUrl  The absolute URL of the page, if known.
 */
record VersionsPlaceholders(
        String pageTitle,
        String pageTitleText,
        @Nullable String description,
        HtmlFragment pageContent,
        @Nullable String canonicalUrl) {
}
