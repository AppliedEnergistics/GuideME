package guideme.internal.web;

import static guideme.internal.web.HtmlUtils.createHtmlElement;
import static guideme.internal.web.HtmlUtils.escapeHtml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
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

    @Nullable
    private final Path folder;
    private final Path outputFolder;

    private final String layoutTemplate;

    public WebAssetsBundle(StaticSiteGenerator.Options options) {
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
                PLACEHOLDER_FOOTER);
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
        var matcher = Pattern.compile("\\{\\{[A-Z0-9_]+}}").matcher(content);
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

    public String realizeLayoutTemplate(WebPageCompileContext context, LayoutPlaceholders placeholders) {
        // Compute the relative path to the output folder to fixup asset links
        var relativePathToRoot = "";
        if (!placeholders.destinationFolder().equals(outputFolder)) {
            relativePathToRoot = placeholders.destinationFolder().relativize(outputFolder).toString().replace('\\', '/')
                    + "/";
        }

        // Strip all HTML and escape
        var titleText = placeholders.pageTitle().replaceAll("<[^>]+>", "") + " - "
                + escapeHtml("AE2 Players Guide for " + context.guide().getGameMajorVersion());

        var guideNavbar = WebGuideNavBar.generate(context);

        var footer = buildFooter(context);

        return layoutTemplate
                .replace(PLACEHOLDER_PAGE_CONTENT, placeholders.pageContent())
                .replace(PLACEHOLDER_PAGE_TITLE, placeholders.pageTitle())
                .replace(PLACEHOLDER_PAGE_TITLE_TEXT, titleText)
                .replace(PLACEHOLDER_RELATIVE_PATH_TO_ROOT, relativePathToRoot)
                .replace(PLACEHOLDER_GUIDE_TITLE, escapeHtml(context.guide().getGuideTitle()))
                .replace(PLACEHOLDER_GUIDE_NAVBAR, guideNavbar)
                .replace(PLACEHOLDER_FOOTER, footer);
    }

    private static @NonNull String buildFooter(WebPageCompileContext context) {
        String footerContent = "Minecraft " + context.guide().getGameMajorVersion();
        String changeVersionUrl = context.options().changeVersionUrl();
        if (changeVersionUrl != null) {
            footerContent += " [" + createHtmlElement("a", Map.of(
                    "href", changeVersionUrl), "change") + "]";
        }
        return createHtmlElement(
                "div",
                Map.of("class", "version-picker"),
                footerContent);
    }

    public void copyToOutputFolder() throws IOException {
        var filesCreated = new HashSet<>();
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
                    filesCreated.add(file.toAbsolutePath().normalize());
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
            if (filesCreated.contains(targetPath)) {
                continue;
            }
            var assetContent = loadDefaultAsset(assetIndexLine);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, assetContent);
        }
    }
}
