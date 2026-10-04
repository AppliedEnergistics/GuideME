package guideme.internal.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guideme.compiler.MdAstNodeAdapter;
import guideme.internal.siteexport.model.IndexModel;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.internal.siteexport.model.SiteExportJson;
import guideme.libs.mdast.model.MdAstNode;
import guideme.siteexport.web.HtmlNode;
import guideme.siteexport.web.WebSiteContribution;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StaticSiteGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(StaticSiteGenerator.class);

    /**
     * Older AE2 exports stored the default config values at the top-level, before mod-specific data was introduced.
     */
    private static final String LEGACY_AE2_CONFIG_VALUES = "defaultConfigValues";
    private static final String AE2_CONFIG_VALUES_KEY = "ae2:default-config-values";

    /**
     * @param title    The title of the guide shown in the header and in the browser title.
     * @param logo     Id of a resource on the classpath to use as the logo, or null to use the GuideME logo.
     * @param favicon  Id of a resource on the classpath to use as the favicon, or null to use the logo.
     * @param siteUrl  The URL of the website (scheme and host, i.e. {@code https://guide.example.com}). Required to
     *                 generate canonical links and the sitemap.
     * @param basePath The URL path the website is served from, which always starts and ends with a slash.
     */
    public record Options(Path dataFolder,
            Path outputFolder,
            @Nullable Path webAssetsPath,
            @Nullable String changeVersionUrl,
            String title,
            @Nullable String logo,
            @Nullable String favicon,
            @Nullable String siteUrl,
            String basePath,
            boolean cleanUrls) {
        public Options(Path dataFolder, Path outputFolder, @Nullable Path webAssetsPath,
                @Nullable String changeVersionUrl) {
            this(dataFolder, outputFolder, webAssetsPath, changeVersionUrl, "Guide", null, null, null, "/", false);
        }

        public Options {
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
    }

    private final Options options;
    private final WebAssetsBundle webAssetsBundle;

    public StaticSiteGenerator(Options options) {
        this.options = options;
        this.webAssetsBundle = new WebAssetsBundle(options);
    }

    static String normalizeBasePath(String basePath) {
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        return basePath;
    }

    public void generate() {
        // Start by reading the index file
        var index = readIndex();

        System.out.println("Minecraft Version: " + index.gameVersion());
        System.out.println("Minecraft Major Version: " + index.gameMajorVersion());
        System.out.println("Mod Version: " + index.modVersion());
        System.out.println("GuideME Version: " + index.guideMeVersion());

        // Copy all content over
        try {
            copyContent();
        } catch (IOException e) {
            System.err.println(
                    "Failed to copy the content from " + options.dataFolder + " to " + options.outputFolder + ": " + e);
            System.exit(1);
        }

        // Copy the web assets over
        try {
            webAssetsBundle.copyToOutputFolder();
        } catch (IOException e) {
            System.err.println("Failed to copy the web assets to " + options.outputFolder + ": " + e);
            System.exit(1);
        }

        var resourceCopier = new WebResourceCopier(options.outputFolder);
        addBranding(resourceCopier);
        addContributions(resourceCopier);

        // Load the guide
        var guide = readGuide(index);
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

    private void addBranding(WebResourceCopier resourceCopier) {
        String logo;
        if (options.logo != null) {
            logo = copyBrandingResource(resourceCopier, options.logo, "logo");
        } else {
            logo = resourceCopier.copyClasspathResource("logo.png", "guideme/logo.png");
        }
        webAssetsBundle.setLogo(logo);

        if (options.favicon != null) {
            webAssetsBundle.setFavicon(copyBrandingResource(resourceCopier, options.favicon, "favicon"));
        } else {
            webAssetsBundle.setFavicon(logo);
        }
    }

    /**
     * Copies a resource given either as a resource id ({@code modid:path} in the assets folder) or as a path on the
     * classpath (such as the mod logo {@code logo.png}).
     */
    private static String copyBrandingResource(WebResourceCopier resourceCopier, String resource, String name) {
        if (resource.contains(":")) {
            return resourceCopier.copy(Identifier.parse(resource));
        }
        var filename = resource.substring(resource.lastIndexOf('/') + 1);
        var extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        return resourceCopier.copyClasspathResource(resource, "site/" + name + extension);
    }

    private void addContributions(WebResourceCopier resourceCopier) {
        for (var contribution : ServiceLoader.load(WebSiteContribution.class,
                WebSiteContribution.class.getClassLoader())) {
            LOG.info("Using website contribution {}", contribution.getClass().getName());
            for (var stylesheet : contribution.getStylesheets()) {
                webAssetsBundle.addStylesheet(resourceCopier.copy(stylesheet));
            }
            for (var script : contribution.getScripts()) {
                webAssetsBundle.addScript(resourceCopier.copy(script));
            }
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

    IndexModel readIndex() {
        var path = options.dataFolder.resolve("index.json");
        try (var reader = Files.newBufferedReader(path)) {
            return new Gson().fromJson(reader, IndexModel.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the index file " + path, e);
        }
    }

    ExportedGuideImpl readGuide(IndexModel index) {
        var path = options.dataFolder.resolve(index.guideDataPath());
        JsonObject json;
        try (var input = Files.newInputStream(path);
                var gzipInput = new GZIPInputStream(new BufferedInputStream(input));
                var reader = new BufferedReader(new InputStreamReader(gzipInput, StandardCharsets.UTF_8))) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the guide data " + path, e);
        }

        var model = new GsonBuilder()
                .registerTypeHierarchyAdapter(MdAstNode.class, new MdAstNodeAdapter())
                .create()
                .fromJson(json, SiteExportJson.class);

        var legacyConfigValues = json.get(LEGACY_AE2_CONFIG_VALUES);
        if (legacyConfigValues != null && !model.modData.containsKey(AE2_CONFIG_VALUES_KEY)) {
            model.modData.put(AE2_CONFIG_VALUES_KEY, new Gson().fromJson(legacyConfigValues, Object.class));
        }

        return new ExportedGuideImpl(index, model);
    }

    public static void main(String[] args) {
        Path dataFolder = null;
        Path outputFolder = null;
        Path webAssetsFolder = null;
        String changeVersionUrl = null;
        String title = "Guide";
        String logo = null;
        String favicon = null;
        String siteUrl = null;
        String basePath = "/";
        boolean cleanUrls = false;
        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            // Flags without value
            if (arg.equals("--clean-urls")) {
                cleanUrls = true;
                continue;
            }

            if (i + 1 >= args.length) {
                exitWithUsage(arg + " requires an argument");
            }
            var value = args[++i];
            switch (arg) {
                case "--data" -> dataFolder = Path.of(value);
                case "-o", "--output" -> outputFolder = Path.of(value);
                case "--web-assets" -> webAssetsFolder = Path.of(value);
                case "--change-version-url" -> changeVersionUrl = value;
                case "--title" -> title = value;
                case "--logo" -> logo = value;
                case "--favicon" -> favicon = value;
                case "--site-url" -> siteUrl = value;
                case "--base-path" -> basePath = value;
                default -> exitWithUsage("Unknown argument: " + arg);
            }
        }
        if (dataFolder == null) {
            exitWithUsage("--data is required");
        }
        if (outputFolder == null) {
            exitWithUsage("--output is required");
        }

        new StaticSiteGenerator(new Options(dataFolder, outputFolder, webAssetsFolder, changeVersionUrl, title, logo,
                favicon, siteUrl, basePath, cleanUrls)).generate();
    }

    private static void exitWithUsage(String error) {
        System.err.println(error);
        System.err.println("""
                Usage: --data <export-folder> --output <destination-folder> [options]
                Options:
                  --title <title>             The title of the guide
                  --logo <id>                 Resource id (modid:textures/logo.png) or classpath path (logo.png)
                                              of the logo
                  --favicon <id>              Like --logo, for the favicon (defaults to the logo)
                  --site-url <url>            URL of the website, i.e. https://guide.example.com
                  --base-path <path>          URL path the website is served from, i.e. /1.21.1/
                  --clean-urls                Write pages as page/index.html to link to them without extension
                  --change-version-url <url>  URL to link to for changing the guide version
                  --web-assets <folder>       Folder with files overriding the default web assets""");
        System.exit(1);
    }
}
