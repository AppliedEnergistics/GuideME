package guideme.internal.web;

import guideme.siteexport.web.HTMLFragment;
import java.nio.file.Path;

record LayoutPlaceholders(
        Path destinationFolder,
        String pageTitle,
        HTMLFragment pageContent) {
}
