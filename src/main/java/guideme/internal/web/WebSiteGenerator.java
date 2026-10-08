package guideme.internal.web;

import guideme.internal.siteexport.model.IndexModel;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.web.html.HtmlNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link guideme.web.WebSiteGenerator}.
 */
public final class WebSiteGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(WebSiteGenerator.class);

    /**
     * @param dataFolder       The folder containing the guide export.
     * @param outputFolder     The folder the website is written to.
     * @param webAssetsPath    A folder whose files override the default web assets.
     * @param changeVersionUrl The URL to link to for changing the guide version.
     * @param title            The title of the guide shown in the header and in the browser title.
     * @param logo             Image file to use as the logo, or null to use the GuideME logo.
     * @param favicon          Image file to use as the favicon, or null to use the logo.
     * @param siteUrl          The URL of the website (scheme and host, i.e. {@code https://guide.example.com}).
     *                         Required to generate canonical links and the sitemap.
     * @param basePath         The URL path the website is served from, which always starts and ends with a slash.
     * @param cleanUrls        Write pages as {@code page/index.html} to link to them without file extension.
     * @param clean            Delete the content of the output folder before generating the website.
     * @param stylesheets      Stylesheet files to include on every page, i.e. to change the look of the website.
     * @param scripts          Script files to include on every page.
     */
    public record Options(Path dataFolder,
            Path outputFolder,
            @Nullable Path webAssetsPath,
            @Nullable String changeVersionUrl,
            String title,
            @Nullable Path logo,
            @Nullable Path favicon,
            @Nullable String siteUrl,
            String basePath,
            boolean cleanUrls,
            boolean clean,
            List<Path> stylesheets,
            List<Path> scripts) {
        public Options(Path dataFolder, Path outputFolder, @Nullable Path webAssetsPath,
                @Nullable String changeVersionUrl) {
            this(dataFolder, outputFolder, webAssetsPath, changeVersionUrl, "Guide", null, null, null, "/", false,
                    false, List.of(), List.of());
        }

        public Options {
            stylesheets = List.copyOf(stylesheets);
            scripts = List.copyOf(scripts);
            basePath = normalizeBasePath(basePath);
            if (siteUrl != null) {
                siteUrl = siteUrl.replaceAll("/+$", "");
            }
        }

        /**
         * {@return the absolute URL of a path relative to the root of the website, or null if no site URL is set}
         */
        @Nullable
        public String absoluteUrl(String pathFromRoot) {
            return siteUrl != null ? siteUrl + basePath + pathFromRoot : null;
        }

        private static String normalizeBasePath(String basePath) {
            if (!basePath.startsWith("/")) {
                basePath = "/" + basePath;
            }
            if (!basePath.endsWith("/")) {
                basePath += "/";
            }
            return basePath;
        }
    }

    private final Options options;
    private final WebAssetsBundle webAssetsBundle;

    public WebSiteGenerator(Options options) {
        this.options = options;
        this.webAssetsBundle = new WebAssetsBundle(options);
    }

    public void generate() {
        // Start by reading the index file
        var index = GuideExportReader.readIndex(options.dataFolder);

        System.out.println("Minecraft Version: " + index.gameVersion());
        System.out.println("Minecraft Major Version: " + index.gameMajorVersion());
        System.out.println("Mod Version: " + index.modVersion());
        System.out.println("GuideME Version: " + index.guideMeVersion());

        try {
            if (options.clean) {
                cleanOutputFolder();
            }

            // Copy all content over
            copyContent();

            // Copy the web assets over
            webAssetsBundle.copyToOutputFolder();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare the output folder " + options.outputFolder, e);
        }

        var resourceCopier = new WebResourceCopier(options.outputFolder);
        addBranding(resourceCopier);
        addSiteResources(resourceCopier);

        // Load the guide
        var guide = GuideExportReader.readGuide(options.dataFolder, index);
        var paths = new SitePaths(guide, options.cleanUrls);

        var compiler = new WebPageCompiler(guide, webAssetsBundle, options, resourceCopier, paths);
        var futures = new ArrayList<CompletableFuture<?>>();
        for (var pageId : guide.getPages().keySet()) {
            futures.add(CompletableFuture.runAsync(() -> compiler.compile(pageId)));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).join();

        compiler.compileNotFoundPage();

        try {
            compiler.getSearchIndex().write(options.outputFolder);
            writeIndexRedirect(guide, paths);
            writeSitemap(guide, paths, index);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes everything in the output folder, so that pages that no longer exist in the export are removed.
     */
    private void cleanOutputFolder() throws IOException {
        var outputFolder = options.outputFolder.toAbsolutePath().normalize();
        if (!Files.isDirectory(outputFolder)) {
            return;
        }

        // Never delete our own input
        for (var input : new Path[] { options.dataFolder, options.webAssetsPath }) {
            if (input != null && input.toAbsolutePath().normalize().startsWith(outputFolder)) {
                throw new IllegalArgumentException("Refusing to clean the output folder " + outputFolder
                        + " since it contains the input " + input);
            }
        }

        LOG.info("Deleting the content of {}", outputFolder);
        Files.walkFileTree(outputFolder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                if (!dir.equals(outputFolder)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void addBranding(WebResourceCopier resourceCopier) {
        var logo = options.logo != null
                ? resourceCopier.copyFile(options.logo)
                : resourceCopier.copy(Identifier.fromNamespaceAndPath("guideme", "logo.png"));
        webAssetsBundle.setLogo(logo);
        webAssetsBundle.setFavicon(options.favicon != null ? resourceCopier.copyFile(options.favicon) : logo);
    }

    /**
     * Adds the stylesheets and scripts of the website, which are included on every page.
     */
    private void addSiteResources(WebResourceCopier resourceCopier) {
        for (var stylesheet : options.stylesheets) {
            webAssetsBundle.addStylesheet(resourceCopier.copyFile(stylesheet));
        }
        for (var script : options.scripts) {
            webAssetsBundle.addScript(resourceCopier.copyFile(script));
        }
    }

    /**
     * Writes an index.html at the root of the website that redirects to the start page, unless the start page is
     * already written there.
     */
    private void writeIndexRedirect(ExportedGuideImpl guide, SitePaths paths) throws IOException {
        var startPageId = guide.getDefaultNamespace() + ":index.md";
        if (!guide.pageExists(startPageId)) {
            startPageId = findFirstPage(guide.getRootNavigationNodes());
            if (startPageId == null) {
                LOG.warn("Not writing index.html since the guide has no pages in its navigation.");
                return;
            }
        }

        if (paths.pageFile(startPageId).equals("index.html")) {
            return; // The start page is already the index page
        }

        var target = paths.pageUrl(startPageId);
        var head = HtmlNode.tag("head")
                .append(HtmlNode.tag("meta").setAttribute("charset", "UTF-8"))
                .append(HtmlNode.tag("meta")
                        .setAttribute("http-equiv", "refresh")
                        .setAttribute("content", "0; url=" + target))
                .append(HtmlNode.tag("title").append(options.title));
        var canonicalUrl = options.absoluteUrl(target);
        if (canonicalUrl != null) {
            head.append(HtmlNode.tag("link").setAttribute("rel", "canonical").setAttribute("href", canonicalUrl));
        }
        var body = HtmlNode.tag("body").append(HtmlNode.tag("a").setAttribute("href", target).append(options.title));
        var html = "<!DOCTYPE html>\n" + HtmlNode.tag("html").setAttribute("lang", "en")
                .append(head).append(body).outerHtml();
        Files.writeString(options.outputFolder.resolve("index.html"), html, StandardCharsets.UTF_8);
    }

    @Nullable
    private static String findFirstPage(List<NavigationNodeJson> nodes) {
        for (var node : nodes) {
            if (node.hasPage) {
                return node.pageId;
            }
            var firstPageId = findFirstPage(node.children);
            if (firstPageId != null) {
                return firstPageId;
            }
        }
        return null;
    }

    /**
     * Writes a sitemap.xml and robots.txt, if the URL of the website is known.
     */
    private void writeSitemap(ExportedGuideImpl guide, SitePaths paths, IndexModel index) throws IOException {
        if (options.siteUrl == null) {
            return;
        }

        var lastModified = DateTimeFormatter.ISO_LOCAL_DATE
                .format(Instant.ofEpochMilli(index.generated()).atOffset(ZoneOffset.UTC));
        var sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        guide.getPages().keySet().stream().sorted().forEach(pageId -> {
            sitemap.append("  <url><loc>")
                    .append(HtmlNode.escapeHtml(Objects.requireNonNull(options.absoluteUrl(paths.pageUrl(pageId)))))
                    .append("</loc><lastmod>").append(lastModified).append("</lastmod></url>\n");
        });
        sitemap.append("</urlset>\n");
        Files.writeString(options.outputFolder.resolve("sitemap.xml"), sitemap, StandardCharsets.UTF_8);

        var robots = "User-agent: *\nAllow: /\n\nSitemap: " + options.absoluteUrl("sitemap.xml") + "\n";
        Files.writeString(options.outputFolder.resolve("robots.txt"), robots, StandardCharsets.UTF_8);
    }

    private void copyContent() throws IOException {
        Path sourceDir = options.dataFolder;
        Path destinationDir = options.outputFolder;
        Files.createDirectories(destinationDir);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(sourceDir)) {
                    Files.createDirectories(destinationDir.resolve(sourceDir.relativize(dir)));
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // Skip files directly in the root
                if (!sourceDir.equals(file.getParent())) {
                    var destination = destinationDir.resolve(sourceDir.relativize(file));
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }

                return super.visitFile(file, attrs);
            }
        });
    }

}
