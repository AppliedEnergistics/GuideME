package guideme.internal.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import guideme.compiler.MdAstNodeAdapter;
import guideme.internal.siteexport.model.IndexModel;
import guideme.internal.siteexport.model.SiteExportJson;
import guideme.libs.mdast.model.MdAstNode;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

class StaticSiteGenerator {
    public record Options(Path dataFolder, Path outputFolder, @Nullable Path webAssetsPath,
            @Nullable String changeVersionUrl) {
    }

    private final Options options;
    private final WebAssetsBundle webAssetsBundle;

    public StaticSiteGenerator(Options options) {
        this.options = options;
        this.webAssetsBundle = new WebAssetsBundle(options);
    }

    private static boolean isIndexPage(String[] pagePath) {
        // Entry-point for the entire version Slug
        return pagePath.length == 1 && pagePath[0].equals("index");
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

        // Load the guide
        var guide = readGuide(index);

        // Return a list of `params` to populate the [slug] dynamic segment
        var compiler = new WebPageCompiler(guide, webAssetsBundle, options);
        var futures = new ArrayList<CompletableFuture<?>>();

        var hadIndexPage = false;
        for (var pageId : guide.getPages().keySet()) {
            var pagePath = guide.getSlugsFromPageId(pageId);
            if (isIndexPage(pagePath)) {
                hadIndexPage = true;
            }
            futures.add(CompletableFuture.runAsync(() -> compiler.compile(pageId)));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).join();

        // Ensure an index page is present. This will be auto-generated on-demand if needed.
        if (!hadIndexPage) {
            // TODO create index page redirecting to first navigation node
        }
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

    private IndexModel readIndex() {
        var path = options.dataFolder.resolve("index.json");
        try (var reader = Files.newBufferedReader(path)) {
            return new Gson().fromJson(reader, IndexModel.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the index file " + path, e);
        }
    }

    private ExportedGuide readGuide(IndexModel index) {
        var path = options.dataFolder.resolve(index.guideDataPath());
        SiteExportJson model;
        try (var input = Files.newInputStream(path);
                var gzipInput = new GZIPInputStream(new BufferedInputStream(input));
                var reader = new BufferedReader(new InputStreamReader(gzipInput, StandardCharsets.UTF_8))) {
            model = new GsonBuilder()
                    .registerTypeHierarchyAdapter(MdAstNode.class, new MdAstNodeAdapter())
                    .create()
                    .fromJson(reader, SiteExportJson.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the index file " + path, e);
        }
        return new ExportedGuide(index, model);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: --data <export-folder> --output <destination-folder>");
            System.exit(1);
        }

        Path dataFolder = null;
        Path outputFolder = null;
        Path webAssetsFolder = null;
        String changeVersionUrl = null;
        for (int i = 0; i < args.length; i++) {
            String next = (i + 1 < args.length) ? args[i + 1] : null;
            var arg = args[i];
            switch (arg) {
                case "--data":
                    if (next == null) {
                        System.err.println(arg + "--data requires an argument");
                        System.exit(1);
                    }
                    dataFolder = Path.of(next);
                    i++; // Skip next
                    break;
                case "-o":
                case "--output":
                    if (next == null) {
                        System.err.println(arg + " requires an argument");
                        System.exit(1);
                    }
                    outputFolder = Path.of(next);
                    i++; // Skip next
                    break;
                case "--web-assets":
                    if (next == null) {
                        System.err.println(arg + " requires an argument");
                        System.exit(1);
                    }
                    webAssetsFolder = Path.of(next);
                    i++; // Skip next
                    break;
                case "--change-version-url":
                    if (next == null) {
                        System.err.println(arg + " requires an argument");
                        System.exit(1);
                    }
                    changeVersionUrl = next;
                    i++; // Skip next
                    break;
                default:
                    System.err.println("Unknown argument: " + arg);
                    System.exit(1);
            }
        }
        if (dataFolder == null) {
            System.err.println("--data is required");
            System.exit(1);
        }
        if (outputFolder == null) {
            System.err.println("--out is required");
            System.exit(1);
        }

        new StaticSiteGenerator(new Options(dataFolder, outputFolder, webAssetsFolder, changeVersionUrl)).generate();
    }
}
