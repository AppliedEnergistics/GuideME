package guideme.web;

import guideme.internal.siteexport.CacheBusting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/**
 * Copies resources from the classpath ({@code assets/<namespace>/<path>}) into the generated website, using
 * cache-busting filenames.
 */
final class WebResourceCopier {
    private static final String OUTPUT_FOLDER = "web-assets";

    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(['\"]?)([^'\")]+)\\1\\s*\\)");

    private final Path outputFolder;
    private final ClassLoader classLoader;

    /**
     * Maps resource id to the path relative to the output folder.
     */
    private final Map<Identifier, String> copiedResources = new HashMap<>();
    private final Set<Identifier> resourcesBeingCopied = new HashSet<>();

    WebResourceCopier(Path outputFolder) {
        this.outputFolder = outputFolder;
        // The website generator runs with GuideME and all mods on the same classpath
        this.classLoader = WebResourceCopier.class.getClassLoader();
    }

    /**
     * Copies the given resource into the website, unless it was already copied.
     *
     * @return The path of the copied resource relative to the root of the website, without a leading slash.
     * @throws IllegalArgumentException If the resource does not exist.
     */
    synchronized String copy(Identifier resourceId) {
        var existing = copiedResources.get(resourceId);
        if (existing != null) {
            return existing;
        }

        if (!resourcesBeingCopied.add(resourceId)) {
            throw new IllegalArgumentException("Circular reference to resource " + resourceId);
        }
        try {
            var path = copyResource(resourceId);
            copiedResources.put(resourceId, path);
            return path;
        } finally {
            resourcesBeingCopied.remove(resourceId);
        }
    }

    /**
     * Copies a resource that is not part of the {@code assets} folder, such as GuideMEs logo, into the website.
     *
     * @param resourcePath The path of the resource on the classpath.
     * @param targetPath   The path to copy the resource to, relative to the folder used for copied resources. A
     *                     cache-busting suffix is added to the filename.
     * @return The path of the copied resource relative to the root of the website, without a leading slash.
     */
    synchronized String copyClasspathResource(String resourcePath, String targetPath) {
        return writeAsset(outputFolder.resolve(OUTPUT_FOLDER).resolve(targetPath), readResource(resourcePath));
    }

    private String copyResource(Identifier resourceId) {
        var content = readResource("assets/" + resourceId.getNamespace() + "/" + resourceId.getPath());

        var targetPath = outputFolder.resolve(OUTPUT_FOLDER)
                .resolve(resourceId.getNamespace())
                .resolve(resourceId.getPath());

        if (resourceId.getPath().endsWith(".css")) {
            content = rewriteStylesheet(resourceId, targetPath.getParent(), content);
        }

        return writeAsset(targetPath, content);
    }

    private byte[] readResource(String resourcePath) {
        try (var in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Couldn't find resource " + resourcePath + " on the classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource " + resourcePath, e);
        }
    }

    private String writeAsset(Path targetPath, byte[] content) {
        try {
            var writtenPath = CacheBusting.writeAsset(targetPath, content);
            return outputFolder.relativize(writtenPath).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + targetPath, e);
        }
    }

    /**
     * Copies resources referenced by relative URLs in the stylesheet and rewrites the URLs to point to the copies.
     */
    private byte[] rewriteStylesheet(Identifier stylesheetId, Path stylesheetFolder, byte[] content) {
        var stylesheet = new String(content, StandardCharsets.UTF_8);
        var baseUri = URI.create("dummy:/" + stylesheetId.getPath());

        var matcher = CSS_URL.matcher(stylesheet);
        var rewritten = matcher.replaceAll(m -> {
            var url = m.group(2).trim();
            if (url.startsWith("data:") || url.startsWith("/") || url.startsWith("#") || url.contains("://")) {
                return Matcher.quoteReplacement(m.group());
            }

            var referencedPath = baseUri.resolve(url).getPath().substring(1);
            var copiedPath = copy(Identifier.fromNamespaceAndPath(stylesheetId.getNamespace(), referencedPath));
            var relativeUrl = stylesheetFolder.relativize(outputFolder.resolve(copiedPath)).toString()
                    .replace('\\', '/');
            return Matcher.quoteReplacement("url(\"" + relativeUrl + "\")");
        });

        return rewritten.getBytes(StandardCharsets.UTF_8);
    }
}
