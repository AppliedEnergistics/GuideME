package guideme.internal.web;

import java.nio.file.Path;

record LayoutPlaceholders(
        Path destinationFolder,
        String pageTitle,
        String pageContent) {
}
