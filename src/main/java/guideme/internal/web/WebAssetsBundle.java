package guideme.internal.web;

import static guideme.internal.web.HtmlUtils.createHtmlElement;
import static guideme.internal.web.HtmlUtils.escapeHtml;

import java.io.IOException;
import java.io.UncheckedIOException;
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

final class WebAssetsBundle {
    private static final String PLACEHOLDER_PAGE_TITLE = "{{PAGE_TITLE}}";
    private static final String PLACEHOLDER_PAGE_TITLE_TEXT = "{{PAGE_TITLE_TEXT}}";
    private static final String PLACEHOLDER_PAGE_CONTENT = "{{PAGE_CONTENT}}";
    private static final String PLACEHOLDER_RELATIVE_PATH_TO_ROOT = "{{RELATIVE_PATH_TO_ROOT}}";
    private static final String PLACEHOLDER_GUIDE_TITLE = "{{GUIDE_TITLE}}";
    private static final String PLACEHOLDER_GUIDE_NAVBAR = "{{GUIDE_NAVBAR}}";
    private static final String PLACEHOLDER_FOOTER = "{{FOOTER}}";

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

    private String loadTemplate(String id, String... supportedPlaceholders) {
        var path = this.folder.resolve("templates/" + id + ".html");
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load template from " + path, e);
        }

        var foundPlaceholders = new HashSet<String>();
        var matcher = Pattern.compile("\\{\\{[A-Z0-9_]+}}").matcher(content);
        while (matcher.find()) {
            foundPlaceholders.add(matcher.group());
        }

        var supportedPlaceholdersSet = Set.of(supportedPlaceholders);
        foundPlaceholders.removeAll(supportedPlaceholdersSet);
        if (!supportedPlaceholdersSet.containsAll(foundPlaceholders)) {
            throw new IllegalArgumentException(path + " has unsupported placeholders: " + foundPlaceholders
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
                return super.visitFile(file, attrs);
            }
        });
    }
}
