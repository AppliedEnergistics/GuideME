package guideme.internal.web;

import guideme.internal.siteexport.CacheBusting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/**
 * Copies resources from the classpath or from files into the generated website, using cache-busting filenames. Relative
 * {@code url(...)} references in stylesheets are resolved against the stylesheet and copied as well.
 */
final class WebResourceCopier {
    private static final String OUTPUT_FOLDER = "web-assets";

    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(['\"]?)([^'\")]+)\\1\\s*\\)");

    private final Path outputFolder;
    private final ClassLoader classLoader;

    /**
     * Maps sources to the path of their copy relative to the output folder.
     */
    private final Map<Source, String> copiedResources = new HashMap<>();
    private final Set<Source> resourcesBeingCopied = new HashSet<>();

    WebResourceCopier(Path outputFolder) {
        this.outputFolder = outputFolder;
        // The website generator runs with GuideME and all mods on the same classpath
        this.classLoader = WebResourceCopier.class.getClassLoader();
    }

    /**
     * Copies the resource {@code assets/<namespace>/<path>} from the classpath.
     *
     * @return The path of the copy relative to the root of the website, without a leading slash.
     * @throws IllegalArgumentException If the resource does not exist.
     */
    String copy(Identifier resourceId) {
        return copyClasspath("assets/" + resourceId.getNamespace() + "/" + resourceId.getPath());
    }

    /**
     * Copies a resource from the classpath.
     *
     * @param resourcePath The path of the resource on the classpath, i.e. {@code modid/web/recipes.css}.
     * @return The path of the copy relative to the root of the website, without a leading slash.
     * @throws IllegalArgumentException If the resource does not exist.
     */
    String copyClasspath(String resourcePath) {
        while (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        return copy(new ClasspathSource(resourcePath));
    }

    /**
     * Copies a file.
     *
     * @return The path of the copy relative to the root of the website, without a leading slash.
     * @throws IllegalArgumentException If the file does not exist.
     */
    String copyFile(Path file) {
        return copy(new FileSource(file.toAbsolutePath().normalize()));
    }

    private synchronized String copy(Source source) {
        var existing = copiedResources.get(source);
        if (existing != null) {
            return existing;
        }

        if (!resourcesBeingCopied.add(source)) {
            throw new IllegalArgumentException("Circular reference to " + source);
        }
        try {
            var content = source.read(classLoader);
            var targetPath = outputFolder.resolve(OUTPUT_FOLDER).resolve(source.outputPath());
            if (source.outputPath().endsWith(".css")) {
                content = rewriteStylesheet(source, targetPath.getParent(), content);
            }

            var writtenPath = CacheBusting.writeAsset(targetPath, content);
            var path = outputFolder.relativize(writtenPath).toString().replace('\\', '/');
            copiedResources.put(source, path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy " + source, e);
        } finally {
            resourcesBeingCopied.remove(source);
        }
    }

    /**
     * Copies resources referenced by relative URLs in the stylesheet and rewrites the URLs to point to the copies.
     */
    private byte[] rewriteStylesheet(Source stylesheet, Path stylesheetFolder, byte[] content) {
        var matcher = CSS_URL.matcher(new String(content, StandardCharsets.UTF_8));
        var rewritten = matcher.replaceAll(m -> {
            var url = m.group(2).trim();
            if (url.startsWith("data:") || url.startsWith("/") || url.startsWith("#") || url.contains("://")) {
                return Matcher.quoteReplacement(m.group());
            }

            var copiedPath = copy(stylesheet.resolve(url));
            var relativeUrl = stylesheetFolder.relativize(outputFolder.resolve(copiedPath)).toString()
                    .replace('\\', '/');
            return Matcher.quoteReplacement("url(\"" + relativeUrl + "\")");
        });

        return rewritten.getBytes(StandardCharsets.UTF_8);
    }

    private sealed interface Source permits ClasspathSource, FileSource {
        byte[] read(ClassLoader classLoader) throws IOException;

        /**
         * {@return the source of a URL relative to this source}
         */
        Source resolve(String relativeUrl);

        /**
         * {@return the path of the copy relative to the folder for copied resources}
         */
        String outputPath();
    }

    private record ClasspathSource(String path) implements Source {
        @Override
        public byte[] read(ClassLoader classLoader) throws IOException {
            try (var in = classLoader.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalArgumentException("Couldn't find resource " + path + " on the classpath");
                }
                return in.readAllBytes();
            }
        }

        @Override
        public Source resolve(String relativeUrl) {
            return new ClasspathSource(URI.create("dummy:/" + path).resolve(relativeUrl).getPath().substring(1));
        }

        @Override
        public String outputPath() {
            // Avoid needless nesting for resources in the assets folder
            return path.startsWith("assets/") ? path.substring("assets/".length()) : path;
        }

        @Override
        public String toString() {
            return "classpath resource " + path;
        }
    }

    private record FileSource(Path file) implements Source {
        @Override
        public byte[] read(ClassLoader classLoader) throws IOException {
            try {
                return Files.readAllBytes(file);
            } catch (NoSuchFileException e) {
                throw new IllegalArgumentException("Couldn't find file " + file);
            }
        }

        @Override
        public Source resolve(String relativeUrl) {
            return new FileSource(file.getParent().resolve(relativeUrl).normalize());
        }

        @Override
        public String outputPath() {
            // Files can be anywhere, so only use their name. The cache-busting suffix prevents collisions.
            return "files/" + file.getFileName();
        }

        @Override
        public String toString() {
            return "file " + file;
        }
    }
}
