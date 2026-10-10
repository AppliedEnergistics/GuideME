package guideme.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import guideme.compiler.MdAstNodeAdapter;
import guideme.compiler.PageCompiler;
import guideme.internal.siteexport.model.ExportedPageJson;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.internal.siteexport.model.SiteExportJson;
import guideme.libs.mdast.model.MdAstNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebSiteGeneratorTest {
    @TempDir
    Path dataFolder;

    @TempDir
    Path outputFolder;

    private SiteExportJson json;

    @BeforeEach
    void setUp() {
        json = new SiteExportJson();
        json.defaultNamespace = "testmod";
        json.pageIndices.put("guideme.indices.ItemIndex", new JsonArray());
        json.pageIndices.put("guideme.indices.CategoryIndex", new JsonArray());
        addPage("testmod:start.md", "# Start Page\n\nWelcome to the **guide**.\n\n- [Other](sub/other.md)");
        addPage("testmod:sub/other.md", "# Other Page\n\nSome text about controllers.\n\n[Back](../start.md)");
    }

    private void addPage(String pageId, String markdown) {
        var page = new ExportedPageJson();
        page.astRoot = PageCompiler.parse("testmod", "en_us", Identifier.parse(pageId), markdown).getAstRoot();
        json.pages.put(pageId, page);

        var node = new NavigationNodeJson();
        node.pageId = pageId;
        node.title = pageId;
        node.hasPage = true;
        node.children = new ArrayList<>();
        json.navigationRootNodes.add(node);
    }

    @Test
    void testDefaultUrls() throws Exception {
        writeExport(toJson());
        new WebSiteGenerator(new WebSiteGenerator.Options(dataFolder, outputFolder, webDist(), null))
                .generate();

        assertThat(outputFolder.resolve("start.html")).exists();
        assertThat(read("sub/other.html")).contains("<a href=\"../start.html\">Back</a>");
        // There's no index page, so the root redirects to the first page
        assertThat(read("index.html")).contains("<meta http-equiv=\"refresh\" content=\"0; url=start.html\"/>");
        // Without a site URL, there's no sitemap
        assertThat(outputFolder.resolve("sitemap.xml")).doesNotExist();
        assertThat(read("start.html")).doesNotContain("rel=\"canonical\"");
    }

    @Test
    void testPageSubdirectoriesAndBasePath() throws Exception {
        generate(true, "/1.21.1/");

        var start = read("start/index.html");
        assertThat(start).contains("<a href=\"../sub/other/\">Other</a>");
        assertThat(start).contains("<link rel=\"canonical\" href=\"https://guide.example.com/1.21.1/start/\"/>");
        assertThat(start).contains("<title>Start Page - Test Guide for Minecraft 26.3</title>");
        assertThat(read("sub/other/index.html")).contains("<a href=\"../../start/\">Back</a>");
        assertThat(read("index.html")).contains("url=start/");

        // The 404 page can be served from any URL, so it uses absolute links
        var notFound = read("404.html");
        assertThat(notFound).contains("<h1>Page Not Found</h1>");
        assertThat(notFound).contains("href=\"/1.21.1/start/\"");
        assertThat(notFound).doesNotContain("href=\"../");
        assertThat(notFound).contains("data-path-to-root=\"/1.21.1/\"");

        assertThat(read("sitemap.xml"))
                .contains("<loc>https://guide.example.com/1.21.1/start/</loc>")
                .contains("<loc>https://guide.example.com/1.21.1/sub/other/</loc>");
        assertThat(read("robots.txt")).contains("Sitemap: https://guide.example.com/1.21.1/sitemap.xml");
    }

    @Test
    void testRootIndexPageIsNotMovedToSubdirectory() throws Exception {
        addPage("testmod:index.md", "# Home\n\n[Other](sub/other.md)");
        addPage("testmod:sub/index.md", "# Sub Index\n\n[Home](../index.md)");
        generate(true, "/");

        assertThat(read("index.html")).contains("<a href=\"sub/other/\">Other</a>");
        assertThat(outputFolder.resolve("index/index.html")).doesNotExist();
        // Only the index page at the root is special
        assertThat(read("sub/index/index.html")).contains("<a href=\"../../\">Home</a>");
        assertThat(read("sitemap.xml")).contains("<loc>https://guide.example.com/</loc>");
    }

    @Test
    void testLogoLinksToStartPage() throws Exception {
        json.startPage = "testmod:sub/other.md";
        generate(true, "/");

        assertThat(read("index.html")).contains("url=sub/other/");
        assertThat(read("start/index.html")).contains("<a href=\"../sub/other/\" class=\"logo\">");
    }

    @Test
    void testSearchIndex() throws Exception {
        generate(false, "/");

        var searchIndex = read(SearchIndex.FILENAME);
        assertThat(searchIndex).contains("{\"url\":\"start.html\",\"title\":\"Start Page\"");
        // Block elements are separated by whitespace, inline elements are not
        assertThat(searchIndex).contains("\"text\":\"Start Page Welcome to the guide. Other\"");
    }

    @Test
    void testLegacyConfigValuesAreMappedToModData() throws Exception {
        var guideJson = toJson();
        var configValues = new JsonObject();
        configValues.addProperty("someValue", "42");
        guideJson.add("defaultConfigValues", configValues);
        writeExport(guideJson);

        var guide = GuideExportReader.readGuide(dataFolder, GuideExportReader.readIndex(dataFolder));

        assertThat(guide.getExtraData("ae2:default-config-values")).isEqualTo(Map.of("someValue", "42"));
    }

    @Test
    void testSiteStylesheetIsIncludedOnEveryPage(@TempDir Path sourceFolder) throws Exception {
        var stylesheet = sourceFolder.resolve("css/site.css");
        Files.createDirectories(stylesheet.getParent());
        Files.writeString(stylesheet, "body { background: url(\"../images/bg.png\"); }");
        Files.createDirectories(sourceFolder.resolve("images"));
        Files.write(sourceFolder.resolve("images/bg.png"), new byte[] { 1, 2, 3 });

        writeExport(toJson());
        new WebSiteGenerator(new WebSiteGenerator.Options(dataFolder, outputFolder, webDist(), null, "Test Guide",
                null, null, null, "/", false, false, List.of(stylesheet), List.of())).generate();

        var link = Pattern
                .compile("<link rel=\"stylesheet\" href=\"(?:\\.\\./)?(web-assets/files/site\\.\\w+\\.css)\"/>");
        var matcher = link.matcher(read("start.html"));
        assertThat(matcher.find()).isTrue();
        assertThat(read("sub/other.html")).containsPattern(link);
        // The image referenced by the stylesheet is copied next to it
        assertThat(read(matcher.group(1))).containsPattern("url\\(\"bg\\.\\w+\\.png\"\\)");
    }

    @Test
    void testRendererStylesheetIsOnlyIncludedWhereUsed() throws Exception {
        addPage("testmod:element.md", "<guidemetest:TestElement item=\"minecraft:stick\" />");
        generate(false, "/");

        var stylesheet = "<link rel=\"stylesheet\" href=\"web-assets/guidemetest/web/test.";
        assertThat(read("element.html")).contains(stylesheet);
        assertThat(read("start.html")).doesNotContain(stylesheet);
    }

    @Test
    void testCleanRemovesStaleFiles() throws Exception {
        var staleFile = outputFolder.resolve("removed/page.html");
        Files.createDirectories(staleFile.getParent());
        Files.writeString(staleFile, "stale");

        writeExport(toJson());
        new WebSiteGenerator(options(false, "/", true)).generate();

        assertThat(staleFile).doesNotExist();
        assertThat(outputFolder.resolve("removed")).doesNotExist();
        assertThat(outputFolder.resolve("start.html")).exists();
    }

    @Test
    void testCleanRefusesToDeleteTheExport(@TempDir Path folder) throws Exception {
        // The export is inside of the output folder
        dataFolder = folder.resolve("export");
        Files.createDirectories(dataFolder);
        writeExport(toJson());
        var options = new WebSiteGenerator.Options(dataFolder, folder, webDist(), null, "Test Guide", null, null,
                null, "/", false, true, List.of(), List.of());

        assertThatThrownBy(() -> new WebSiteGenerator(options).generate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refusing to clean");
        assertThat(dataFolder.resolve("index.json")).exists();
    }

    @Test
    void testVersionedWebSite() throws Exception {
        writeExport(dataFolder.resolve("minecraft-1.21.1"), "1.21.1", toJson());
        writeExport(dataFolder.resolve("minecraft-26.3"), "26.3", toJson());
        // Folders without an export are ignored
        Files.createDirectories(dataFolder.resolve("unrelated"));
        generateVersioned("https://dev.example.com/");

        var start = read("26.3/start/index.html");
        assertThat(start).contains("<link rel=\"canonical\" href=\"https://guide.example.com/26.3/start/\"/>");
        assertThat(start).contains("<a href=\"/\">change</a>");
        assertThat(read("1.21.1/sub/other/index.html")).contains("<a href=\"../../start/\">Back</a>");
        assertThat(outputFolder.resolve("unrelated")).doesNotExist();

        // Crawlers find the sitemaps of all versions through the root
        assertThat(outputFolder.resolve("26.3/robots.txt")).doesNotExist();
        assertThat(read("26.3/sitemap.xml")).contains("<loc>https://guide.example.com/26.3/start/</loc>");
        assertThat(read("robots.txt")).contains("Sitemap: https://guide.example.com/sitemap.xml");
        assertThat(read("sitemap.xml"))
                .contains("<sitemapindex")
                .containsSubsequence("<loc>https://guide.example.com/26.3/sitemap.xml</loc>",
                        "<loc>https://guide.example.com/1.21.1/sitemap.xml</loc>");

        // The newest version comes first
        var index = read("index.html");
        assertThat(index).containsSubsequence("href=\"/26.3/\"", "Latest", "href=\"/1.21.1/\"");
        assertThat(index).contains("<a href=\"https://dev.example.com/\">development version</a>");
        assertThat(index).contains("<link rel=\"canonical\" href=\"https://guide.example.com/\"/>");

        var notFound = read("404.html");
        assertThat(notFound).contains("<h1>Page Not Found</h1>");
        assertThat(notFound).contains("href=\"/26.3/\"");
        assertThat(notFound).doesNotContain("development version");
    }

    @Test
    void testVersionedWebSiteSortsVersionsNumerically() throws Exception {
        for (var version : List.of("1.20.9", "1.20.10", "1.9", "26.1")) {
            writeExport(dataFolder.resolve(version), version, toJson());
        }
        var options = new VersionedWebSiteGenerator.Options(options(true, "/", false), null);

        var versions = new VersionedWebSiteGenerator(options).findVersions();

        assertThat(versions).extracting(VersionedWebSiteGenerator.Version::path)
                .containsExactly("26.1", "1.20.10", "1.20.9", "1.9");
    }

    @Test
    void testVersionedWebSiteRejectsDuplicateVersions() throws Exception {
        writeExport(dataFolder.resolve("a"), "26.3", toJson());
        writeExport(dataFolder.resolve("b"), "26.3", toJson());

        assertThatThrownBy(() -> generateVersioned(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("are both for Minecraft 26.3");
    }

    private void generateVersioned(String developmentUrl) {
        new VersionedWebSiteGenerator(new VersionedWebSiteGenerator.Options(options(true, "/", false),
                developmentUrl)).generate();
    }

    private void generate(boolean pageSubdirectories, String basePath) throws IOException {
        writeExport(toJson());
        new WebSiteGenerator(options(pageSubdirectories, basePath, false)).generate();
    }

    private WebSiteGenerator.Options options(boolean pageSubdirectories, String basePath, boolean clean) {
        return new WebSiteGenerator.Options(dataFolder, outputFolder, webDist(), null, "Test Guide", null, null,
                "https://guide.example.com", basePath, pageSubdirectories, clean, List.of(), List.of());
    }

    private static Path webDist() {
        return Path.of(System.getProperty("guideme.web.dist", "web/dist"));
    }

    private JsonObject toJson() {
        return gson().toJsonTree(json).getAsJsonObject();
    }

    private void writeExport(JsonObject guideJson) throws IOException {
        writeExport(dataFolder, "26.3", guideJson);
    }

    private static void writeExport(Path folder, String gameVersion, JsonObject guideJson) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("index.json"), """
                {"format": 1, "generated": 0, "gameMajorVersion": "%s", "gameVersion": "%s",
                 "modVersion": "1.0.0", "guideMeVersion": "1.0.0", "guideDataPath": "guide.json.gz"}"""
                .formatted(gameVersion, gameVersion));
        try (var out = new GZIPOutputStream(Files.newOutputStream(folder.resolve("guide.json.gz")))) {
            out.write(gson().toJson(guideJson).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Gson gson() {
        return new GsonBuilder().registerTypeHierarchyAdapter(MdAstNode.class, new MdAstNodeAdapter()).create();
    }

    private String read(String path) throws IOException {
        return Files.readString(outputFolder.resolve(path));
    }
}
