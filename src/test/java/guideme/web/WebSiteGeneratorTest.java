package guideme.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        page.astRoot = PageCompiler.parse("testmod", Identifier.parse(pageId), markdown).getAstRoot();
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
    void testCleanUrlsAndBasePath() throws Exception {
        generate(true, "/1.21.1/");

        var start = read("start/index.html");
        assertThat(start).contains("<a href=\"../sub/other/\">Other</a>");
        assertThat(start).contains("<link rel=\"canonical\" href=\"https://guide.example.com/1.21.1/start/\"/>");
        assertThat(start).contains("data-base-path=\"/1.21.1/\"");
        assertThat(start).contains("<title>Start Page - Test Guide for Minecraft 26.3</title>");
        assertThat(read("sub/other/index.html")).contains("<a href=\"../../start/\">Back</a>");
        assertThat(read("index.html")).contains("url=start/");

        // The 404 page can be served from any URL, so it uses absolute links
        var notFound = read("404.html");
        assertThat(notFound).contains("<h1>Page Not Found</h1>");
        assertThat(notFound).contains("href=\"/1.21.1/start/\"");
        assertThat(notFound).doesNotContain("href=\"../");

        assertThat(read("sitemap.xml"))
                .contains("<loc>https://guide.example.com/1.21.1/start/</loc>")
                .contains("<loc>https://guide.example.com/1.21.1/sub/other/</loc>");
        assertThat(read("robots.txt")).contains("Sitemap: https://guide.example.com/1.21.1/sitemap.xml");
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

        assertThat(guide.getExtraData("ae2:default-config-values")).isEqualTo(java.util.Map.of("someValue", "42"));
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
                null, "/", false, true);

        assertThatThrownBy(() -> new WebSiteGenerator(options).generate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refusing to clean");
        assertThat(dataFolder.resolve("index.json")).exists();
    }

    private void generate(boolean cleanUrls, String basePath) throws IOException {
        writeExport(toJson());
        new WebSiteGenerator(options(cleanUrls, basePath, false)).generate();
    }

    private WebSiteGenerator.Options options(boolean cleanUrls, String basePath, boolean clean) {
        return new WebSiteGenerator.Options(dataFolder, outputFolder, webDist(), null, "Test Guide", null, null,
                "https://guide.example.com", basePath, cleanUrls, clean);
    }

    private static Path webDist() {
        return Path.of(System.getProperty("guideme.web.dist", "web/dist"));
    }

    private JsonObject toJson() {
        return gson().toJsonTree(json).getAsJsonObject();
    }

    private void writeExport(JsonObject guideJson) throws IOException {
        Files.writeString(dataFolder.resolve("index.json"), """
                {"format": 1, "generated": 0, "gameMajorVersion": "26.3", "gameVersion": "26.3",
                 "modVersion": "1.0.0", "guideMeVersion": "1.0.0", "guideDataPath": "guide.json.gz"}""");
        try (var out = new GZIPOutputStream(Files.newOutputStream(dataFolder.resolve("guide.json.gz")))) {
            out.write(gson().toJson(guideJson).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static com.google.gson.Gson gson() {
        return new GsonBuilder().registerTypeHierarchyAdapter(MdAstNode.class, new MdAstNodeAdapter()).create();
    }

    private String read(String path) throws IOException {
        return Files.readString(outputFolder.resolve(path));
    }
}
