package guideme.internal.web;

import guideme.internal.siteexport.model.ExportedPageJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

record WebPageCompileContext(
        StaticSiteGenerator.Options options,
        ExportedGuide guide,
        String pageId,
        ExportedPageJson page,
        WebPageCompiler.TemplateContainer templates) {
    public String resolveAssetPath(String absoluteAssetPath) {
        var relativeAssetPath = absoluteAssetPath;
        while (relativeAssetPath.startsWith("/")) {
            relativeAssetPath = relativeAssetPath.substring(1);
        }

        var assetPath = options.outputFolder().resolve(relativeAssetPath);
        if (!Files.isRegularFile(assetPath)) {
            throw new IllegalArgumentException("Missing asset: " + assetPath);
        }

        try {
            return resolveOutputPath(guide.getPageBasePath(pageId)).getParent().relativize(assetPath).toString()
                    .replace('\\', '/');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to resolve asset path from " + pageId + " to " + assetPath, e);
        }
    }

    public Path resolveOutputPath(String relativePath) throws IOException {
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

    public String getRelativePagePath(String pageId) {
        return guide.getRelativePagePath(pageId, this.pageId);
    }

    public String getUrlPrefixToRoot() {
        var relativePathToRoot = "";
        var pageFolder = options().outputFolder().resolve(guide.getPagePath(pageId())).getParent();
        if (!options().outputFolder().equals(pageFolder)) {
            relativePathToRoot = pageFolder.relativize(options().outputFolder()).toString().replace('\\', '/') + "/";
        }
        return relativePathToRoot;
    }
}
