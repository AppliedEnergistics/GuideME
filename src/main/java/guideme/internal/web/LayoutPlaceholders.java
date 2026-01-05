package guideme.internal.web;

import guideme.siteexport.web.HtmlFragment;
import java.nio.file.Path;

record LayoutPlaceholders(
        Path destinationFolder,
        String pageTitle,
        HtmlFragment pageContent) {
}
