package guideme.web;

import org.jspecify.annotations.Nullable;

/**
 * @param pageTitle    The page title as plain text.
 * @param canonicalUrl The absolute URL of the page, if known.
 */
record LayoutPlaceholders(
        String pageTitle,
        HtmlFragment pageContent,
        @Nullable String canonicalUrl) {
}
