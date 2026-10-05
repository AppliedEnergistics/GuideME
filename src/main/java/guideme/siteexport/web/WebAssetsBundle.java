package guideme.siteexport.web;

import static guideme.siteexport.web.HtmlUtils.escapeHtml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WebAssetsBundle {
    private static final Logger LOG = LoggerFactory.getLogger(WebAssetsBundle.class);

    private static final String DEFAULT_ASSET_BASE = "/guideme/internal/web/default-assets/";

    private static final String PLACEHOLDER_PAGE_TITLE = "{{PAGE_TITLE}}";
    private static final String PLACEHOLDER_PAGE_TITLE_TEXT = "{{PAGE_TITLE_TEXT}}";
    private static final String PLACEHOLDER_PAGE_CONTENT = "{{PAGE_CONTENT}}";
    private static final String PLACEHOLDER_RELATIVE_PATH_TO_ROOT = "{{RELATIVE_PATH_TO_ROOT}}";
    private static final String PLACEHOLDER_GUIDE_TITLE = "{{GUIDE_TITLE}}";
    private static final String PLACEHOLDER_GUIDE_NAVBAR = "{{GUIDE_NAVBAR}}";
    private static final String PLACEHOLDER_FOOTER = "{{FOOTER}}";
    private static final String PLACEHOLDER_EXTRA_HEAD = "{{EXTRA_HEAD}}";
    private static final String PLACEHOLDER_LOGO_URL = "{{LOGO_URL}}";
    private static final String PLACEHOLDER_HOME_URL = "{{HOME_URL}}";
    private static final String PLACEHOLDER_BASE_PATH = "{{BASE_PATH}}";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[A-Z0-9_]+}}");

    @Nullable
    private final Path folder;
    private final Path outputFolder;

    private final String layoutTemplate;

    /**
     * Paths of stylesheets and scripts relative to the output folder, which are added to the head of every page.
     */
    private final List<String> extraStylesheets = new ArrayList<>();
    private final List<String> extraScripts = new ArrayList<>();

    /**
     * Paths of the logo and favicon relative to the output folder.
     */
    private String logo = "";
    @Nullable
    private String favicon;

    WebAssetsBundle(WebSiteGenerator.Options options) {
        this.folder = options.webAssetsPath();
        this.outputFolder = options.outputFolder();
        this.layoutTemplate = loadTemplate(
                "layout",
                PLACEHOLDER_PAGE_TITLE,
                PLACEHOLDER_PAGE_TITLE_TEXT,
                PLACEHOLDER_PAGE_CONTENT,
                PLACEHOLDER_RELATIVE_PATH_TO_ROOT,
                PLACEHOLDER_GUIDE_TITLE,
                PLACEHOLDER_GUIDE_NAVBAR,
                PLACEHOLDER_FOOTER,
                PLACEHOLDER_EXTRA_HEAD,
                PLACEHOLDER_LOGO_URL,
                PLACEHOLDER_HOME_URL,
                PLACEHOLDER_BASE_PATH);
    }

    void setLogo(String pathInOutputFolder) {
        this.logo = pathInOutputFolder;
    }

    void setFavicon(String pathInOutputFolder) {
        this.favicon = pathInOutputFolder;
    }

    void addStylesheet(String pathInOutputFolder) {
        extraStylesheets.add(pathInOutputFolder);
    }

    void addScript(String pathInOutputFolder) {
        extraScripts.add(pathInOutputFolder);
    }

    private byte[] loadAsset(String name) throws IOException {
        if (this.folder != null) {
            var path = this.folder.resolve(name);
            if (Files.exists(path)) {
                LOG.info("Loading asset {} from override {}", name, path);
                return Files.readAllBytes(path);
            }
        }

        return loadDefaultAsset(name);
    }

    private byte[] loadDefaultAsset(String name) throws IOException {
        try (var input = getClass().getResourceAsStream(DEFAULT_ASSET_BASE + name)) {
            if (input == null) {
                throw new FileNotFoundException(name);
            }
            return input.readAllBytes();
        }
    }

    private String loadTemplate(String id, String... supportedPlaceholders) {
        String content;
        try {
            content = new String(loadAsset("templates/" + id + ".html"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template " + id, e);
        }

        var foundPlaceholders = new HashSet<String>();
        var matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            foundPlaceholders.add(matcher.group());
        }

        var supportedPlaceholdersSet = Set.of(supportedPlaceholders);
        foundPlaceholders.removeAll(supportedPlaceholdersSet);
        if (!supportedPlaceholdersSet.containsAll(foundPlaceholders)) {
            throw new IllegalArgumentException("Template " + id + " has unsupported placeholders: " + foundPlaceholders
                    + ". Supported: " + supportedPlaceholdersSet);
        }
        return content;
    }

    String realizeLayoutTemplate(WebPageCompileContext context, LayoutPlaceholders placeholders) {
        var options = context.options();

        // The page title is plain text
        var titleText = escapeHtml(placeholders.pageTitle() + " - " + options.title() + " for Minecraft "
                + context.guide().getGameMajorVersion());

        var guideNavbar = WebGuideNavBar.generate(context);

        var footer = buildFooter(context);

        var extraHead = new HtmlFragment();
        if (placeholders.canonicalUrl() != null) {
            extraHead.append(HtmlNode.tag("link")
                    .setAttribute("rel", "canonical")
                    .setAttribute("href", placeholders.canonicalUrl()));
        }
        if (favicon != null) {
            extraHead.append(HtmlNode.tag("link")
                    .setAttribute("rel", "icon")
                    .setAttribute("href", context.url(favicon)));
        }
        for (var stylesheet : extraStylesheets) {
            extraHead.append(HtmlNode.tag("link")
                    .setAttribute("rel", "stylesheet")
                    .setAttribute("href", context.url(stylesheet)));
        }
        for (var script : extraScripts) {
            extraHead.append(HtmlNode.tag("script")
                    .setAttribute("src", context.url(script))
                    .setAttribute("defer", null));
        }

        var values = new HashMap<String, String>();
        values.put(PLACEHOLDER_PAGE_CONTENT, placeholders.pageContent().outerHtml());
        values.put(PLACEHOLDER_PAGE_TITLE, escapeHtml(placeholders.pageTitle()));
        values.put(PLACEHOLDER_PAGE_TITLE_TEXT, titleText);
        values.put(PLACEHOLDER_RELATIVE_PATH_TO_ROOT, context.getUrlPrefixToRoot());
        values.put(PLACEHOLDER_GUIDE_TITLE, escapeHtml(options.title()));
        values.put(PLACEHOLDER_GUIDE_NAVBAR, guideNavbar.outerHtml());
        values.put(PLACEHOLDER_FOOTER, footer.outerHtml());
        values.put(PLACEHOLDER_EXTRA_HEAD, extraHead.outerHtml());
        values.put(PLACEHOLDER_LOGO_URL, escapeHtml(context.url(logo)));
        values.put(PLACEHOLDER_HOME_URL, escapeHtml(context.url("")));
        values.put(PLACEHOLDER_BASE_PATH, escapeHtml(options.basePath()));

        // Replace in a single pass, so that placeholders in the inserted content are not replaced
        return PLACEHOLDER_PATTERN.matcher(layoutTemplate)
                .replaceAll(m -> Matcher.quoteReplacement(values.getOrDefault(m.group(), m.group())));
    }

    private static HtmlTag buildFooter(WebPageCompileContext context) {
        var fragment = new HtmlFragment();
        fragment.append("Minecraft " + context.guide().getGameMajorVersion());

        // When the website is served from a sub-path, assume that the root lists the available versions
        var options = context.options();
        var changeVersionUrl = options.changeVersionUrl();
        if (changeVersionUrl == null && !options.basePath().equals("/")) {
            changeVersionUrl = "/";
        }
        if (changeVersionUrl != null) {
            fragment.append(" [");
            fragment.append(HtmlNode.tag("a")
                    .setAttribute("href", changeVersionUrl).append("change"));
            fragment.append("]");
        }

        return HtmlNode.tag("div", fragment)
                .setClassName("version-picker");
    }

    void copyToOutputFolder() throws IOException {
        var filesCreated = new HashSet<Path>();
        if (this.folder != null) {
            Path templatesFolder = folder.resolve("templates");
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (templatesFolder.equals(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(outputFolder.resolve(folder.relativize(dir)));
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    var destination = outputFolder.resolve(folder.relativize(file));
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                    filesCreated.add(destination.toAbsolutePath().normalize());
                    return super.visitFile(file, attrs);
                }
            });
        }

        // Copy over default assets unless they were already overridden
        // In dev, the default assets may be missing
        String[] assetIndexLines;
        try {
            assetIndexLines = new String(loadDefaultAsset("index.txt"), StandardCharsets.UTF_8).split("\n");
        } catch (FileNotFoundException e) {
            LOG.warn("Not copying default assets, since they're missing.");
            return;
        }
        for (var assetIndexLine : assetIndexLines) {
            assetIndexLine = assetIndexLine.trim();
            if (assetIndexLine.isEmpty()) {
                continue;
            }
            var targetPath = outputFolder.resolve(assetIndexLine);
            if (filesCreated.contains(targetPath.toAbsolutePath().normalize())) {
                continue;
            }
            var assetContent = loadDefaultAsset(assetIndexLine);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, assetContent);
        }
    }
}
