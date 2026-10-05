package guideme.web;

import guideme.internal.siteexport.model.ExportedPageJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @param urlPrefix The prefix to add to paths relative to the root of the website to link to them from this page. This
 *                  is a relative path such as "../" for normal pages, or the base path of the website for pages that
 *                  may be served from any URL, such as the 404 page.
 */
record WebPageCompileContext(
        WebSiteGenerator.Options options,
        ExportedGuideImpl guide,
        SitePaths paths,
        String pageId,
        ExportedPageJson page,
        String urlPrefix,
        WebPageCompiler.TemplateContainer templates) {

    /**
     * {@return the URL to link to the given path relative to the root of the website from this page}
     */
    String url(String pathFromRoot) {
        while (pathFromRoot.startsWith("/")) {
            pathFromRoot = pathFromRoot.substring(1);
        }
        var url = urlPrefix + pathFromRoot;
        return url.isEmpty() ? "./" : url;
    }

    String resolveAssetPath(String absoluteAssetPath) {
        var relativeAssetPath = absoluteAssetPath;
        while (relativeAssetPath.startsWith("/")) {
            relativeAssetPath = relativeAssetPath.substring(1);
        }

        var assetPath = options.outputFolder().resolve(relativeAssetPath);
        if (!Files.isRegularFile(assetPath)) {
            throw new IllegalArgumentException("Missing asset: " + assetPath);
        }

        return url(relativeAssetPath);
    }

    Path resolveOutputPath(String relativePath) throws IOException {
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        var result = this.options.outputFolder().resolve(relativePath);
        synchronized (guide) {
            var parentDir = result.getParent();
            Files.createDirectories(parentDir);
        }
        return result;
    }

    String getRelativePagePath(String pageId) {
        return url(paths.pageUrl(pageId));
    }

    String getUrlPrefixToRoot() {
        return urlPrefix;
    }
}
