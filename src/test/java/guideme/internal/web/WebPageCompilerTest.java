package guideme.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import guideme.compiler.PageCompiler;
import guideme.internal.siteexport.model.ExportedPageJson;
import guideme.internal.siteexport.model.IndexModel;
import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.internal.siteexport.model.SiteExportJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebPageCompilerTest {
    private static final String PAGE_ID = "testmod:page.md";

    @TempDir
    Path outputFolder;

    private SiteExportJson json;

    @BeforeEach
    void setUp() throws IOException {
        json = new SiteExportJson();
        json.defaultNamespace = "testmod";
        json.pageIndices.put("guideme.indices.ItemIndex", new JsonArray());
        json.pageIndices.put("guideme.indices.CategoryIndex", new JsonArray());

        addItem("minecraft:stick", "Stick");
        addItem("minecraft:oak_planks", "Oak Planks");
        addItem("minecraft:crafting_table", "Crafting Table");

        json.recipes.put("minecraft:stick", JsonParser.parseString("""
                {
                  "type": "minecraft:crafting",
                  "resultItem": "minecraft:stick",
                  "resultCount": 4,
                  "shapeless": false,
                  "width": 1,
                  "height": 2,
                  "ingredients": [["minecraft:oak_planks"], ["minecraft:oak_planks"]]
                }""").getAsJsonObject());
        // AE2 entropy recipes have no result item
        json.recipes.put("ae2:entropy", JsonParser.parseString("""
                {"type": "ae2:entropy"}""").getAsJsonObject());

        Files.writeString(outputFolder.resolve("scene.png"), "");
        Files.writeString(outputFolder.resolve("scene.scene.gz"), "");
    }

    private void addItem(String id, String displayName) throws IOException {
        var item = new ItemInfoJson();
        item.id = id;
        item.displayName = displayName;
        item.rarity = "common";
        item.icon = "/icons/" + id.replace(':', '_') + ".png";
        json.items.put(id, item);

        var iconPath = outputFolder.resolve(item.icon.substring(1));
        Files.createDirectories(iconPath.getParent());
        Files.writeString(iconPath, "");
    }

    @Test
    void testTooltipTemplatesContainHtml() throws Exception {
        var html = compile("<ItemLink id=\"minecraft:stick\" />");

        assertThat(html).contains("<template id=\"tmpl-1\"><img class=\"item-icon\"");
        assertThat(html).doesNotContain("&lt;img");
    }

    @Test
    void testItemLinkWithoutContentUsesItemName() throws Exception {
        var html = compile("<ItemLink id=\"minecraft:stick\" />");

        assertThat(html).contains("<span class=\"item-link\">Stick</span>");
    }

    @Test
    void testClassNameOnFlowAndTextElements() throws Exception {
        var html = compile("""
                <div className="flow">a</div>

                Some <span class="text">b</span> text""");

        assertThat(html).contains("<div class=\"flow\">");
        assertThat(html).contains("<span class=\"text\">b</span>");
    }

    @Test
    void testBothClassAndClassNameIsAnError() throws Exception {
        var html = compile("<div class=\"a\" className=\"b\">x</div>");

        assertThat(html).contains("Both class and className specified");
    }

    @Test
    void testBooleanAttribute() throws Exception {
        var html = compile("<details open>x</details>");

        assertThat(html).contains("<details open>");
    }

    @Test
    void testInlinePageTitleWrapper() throws Exception {
        var html = compile("# Title & More\n\nText");

        assertThat(html).contains("<div class=\"inlinePageTitle\"><h1>Title &amp; More</h1></div>");
        assertThat(html).contains("<title>Title &amp; More - ");
    }

    @Test
    void testPageWithoutHeading() throws Exception {
        var html = compile("Just text");

        assertThat(html).contains("<p>Just text</p>");
    }

    @Test
    void testOrderedListStart() throws Exception {
        var html = compile("3. a\n4. b");

        assertThat(html).contains("<ol start=\"3\">");
    }

    @Test
    void testTightListUnwrapsParagraphs() throws Exception {
        var html = compile("- a\n- b");

        assertThat(html).contains("<ul><li>a</li><li>b</li></ul>");
    }

    @Test
    void testLooseListKeepsParagraphs() throws Exception {
        var html = compile("- a\n\n- b");

        assertThat(html).contains("<li>\n<p>a</p>\n</li>");
    }

    @Test
    void testNoWhitespaceBetweenInlineElements() throws Exception {
        var html = compile("**bold**, text");

        assertThat(html).contains("<strong>bold</strong>, text");
    }

    @Test
    void testRecipeHasSingleContainer() throws Exception {
        var html = compile("<Recipe id=\"minecraft:stick\" />");

        assertThat(html).contains("<div class=\"recipe-container\"><div class=\"minecraft-frame\">");
        assertThat(html).doesNotContain("<div class=\"recipe-container\"><div class=\"recipe-container\">");
    }

    @Test
    void testMissingRecipeFallbackText() throws Exception {
        assertThat(compile("<Recipe id=\"missing\" fallbackText=\"Disabled\" />"))
                .contains("<p>Disabled</p>")
                .doesNotContain("Missing recipe");
        assertThat(compile("<Recipe id=\"missing\" fallbackText=\"\" />"))
                .doesNotContain("Missing recipe");
        assertThat(compile("<Recipe id=\"missing\" />"))
                .contains("Missing recipe");
    }

    @Test
    void testUnknownItemsAreErrors() throws Exception {
        assertThat(compile("<ItemImage id=\"missing\" />")).contains("Missing item missing");
        assertThat(compile("<ItemIcon id=\"missing\" />")).contains("Missing item missing");
    }

    @Test
    void testMalformedAttributeOnlyAffectsElement() throws Exception {
        var html = compile("""
                Before

                <GameScene src="/scene.scene.gz" placeholder="/scene.png" width="abc" height="100" />

                After""");

        assertThat(html).contains("Failed to compile GameScene: Expected integer value for attribute width");
        assertThat(html).contains("<p>Before</p>").contains("<p>After</p>");
    }

    @Test
    void testSubPages() throws Exception {
        var child = new NavigationNodeJson();
        child.pageId = "testmod:child.md";
        child.title = "Child";
        child.hasPage = true;
        child.children = new ArrayList<>();

        var self = new NavigationNodeJson();
        self.pageId = PAGE_ID;
        self.title = "Page";
        self.hasPage = true;
        self.children = new ArrayList<>(List.of(child));
        json.navigationRootNodes.add(self);

        var html = compile("<SubPages />");

        assertThat(html).contains("<ul class=\"sub-pages\"><li><a href=\"child.html\">Child</a></li></ul>");
    }

    @Test
    void testGameSceneAnnotations() throws Exception {
        var html = compile(
                """
                        <GameScene src="/scene.scene.gz" placeholder="/scene.png" width="100" height="100" interactive fullWidth>
                          <BoxAnnotation min="0 0 0" max="1 1 1" alwaysOnTop={true}>
                            Box
                          </BoxAnnotation>
                          <BlockAnnotation pos="1 2 3" />
                          <LineAnnotation from="0 0 0" to="1 1 1" />
                          <DiamondAnnotation pos="0.5 0.5 0.5" />
                          <Unknown />
                        </GameScene>""");

        assertThat(html).contains("data-scene-interactive=\"true\"");
        assertThat(html).contains("&quot;minCorner&quot;:[0.0,0.0,0.0]");
        assertThat(html).contains("&quot;contentTemplateId&quot;:&quot;tmpl-1&quot;,&quot;alwaysOnTop&quot;:true");
        assertThat(html).contains("<template id=\"tmpl-1\"><p>Box</p></template>");
        assertThat(html).contains("&quot;minCorner&quot;:[1.0,2.0,3.0],&quot;maxCorner&quot;:[2.0,3.0,4.0]");
        assertThat(html).contains("&quot;from&quot;:[0.0,0.0,0.0],&quot;to&quot;:[1.0,1.0,1.0]");
        assertThat(html).contains("&quot;position&quot;:[0.5,0.5,0.5]");
        assertThat(html).contains("Unsupported child tag Unknown");
    }

    @Test
    void testBuiltInTags() throws Exception {
        var html = compile("""
                <Color color="#ff0000">red</Color> <Color color="#80ff0000">alpha</Color> <Color id="link">link</Color>
                <KeyBind id="key.jump" keyName="Space" /> <KeyBind id="key.sneak" />
                <PlayerName /> <CommandLink command="/help" title="Help">run</CommandLink>""");

        assertThat(html).doesNotContain("Unhandled custom element");
        assertThat(html).contains("<span style=\"color: #ff0000\">red</span>");
        assertThat(html).contains("<span style=\"color: #ff000080\">alpha</span>");
        assertThat(html).contains("<span style=\"color: #00d5ffff\">link</span>");
        assertThat(html).contains("<kbd>Space</kbd>");
        assertThat(html).contains("<kbd>key.sneak</kbd>");
        assertThat(html).contains("<span class=\"command-link\" title=\"Help\n/help\">run</span>");
    }

    @Test
    void testCustomElementRendererFromServiceLoader() throws Exception {
        var otherPage = new ExportedPageJson();
        otherPage.astRoot = PageCompiler.parse("testmod", Identifier.parse("testmod:other.md"), "Other").getAstRoot();
        json.pages.put("testmod:other.md", otherPage);

        var html = compile("<guidemetest:TestElement item=\"minecraft:stick\">**child**</guidemetest:TestElement>");

        assertThat(html).doesNotContain("Error:");
        assertThat(html)
                .contains("<div class=\"test-element\"><img class=\"item-icon\" src=\"icons/minecraft_stick.png\"");
        assertThat(html).contains("<span class=\"item-link\">link text</span>");
        assertThat(html).contains("<a href=\"other.html\">");
        assertThat(html)
                .containsPattern("<img src=\"web-assets/guidemetest/web/images/test\\.[A-Za-z0-9]{12}\\.png\"/>");
        assertThat(html).contains("<strong>child</strong>");
    }

    @Test
    void testStylesheetReferencesAreCopied() throws Exception {
        var copier = new WebResourceCopier(outputFolder);
        var stylesheetPath = copier.copy(Identifier.fromNamespaceAndPath("guidemetest", "web/test.css"));

        assertThat(stylesheetPath).matches("web-assets/guidemetest/web/test\\.[A-Za-z0-9]{12}\\.css");
        var stylesheet = Files.readString(outputFolder.resolve(stylesheetPath));
        var imagePath = copier.copy(Identifier.fromNamespaceAndPath("guidemetest", "web/images/test.png"));
        assertThat(stylesheet).contains("url(\"" + imagePath.substring("web-assets/guidemetest/web/".length()) + "\")");
        assertThat(stylesheet).contains("url(data:image/png;base64,AAAA)");
        assertThat(outputFolder.resolve(imagePath)).exists();
    }

    private String compile(String markdown) throws IOException {
        var page = new ExportedPageJson();
        page.astRoot = PageCompiler.parse("testmod", Identifier.parse(PAGE_ID), markdown).getAstRoot();
        json.pages.put(PAGE_ID, page);

        var index = new IndexModel(1, 0, "26.3", "26.3", "26.3", true, "1.0.0", "1.0.0", "guide.json.gz");
        var webDist = Path.of(System.getProperty("guideme.web.dist", "web/dist"));
        var options = new StaticSiteGenerator.Options(outputFolder, outputFolder, webDist, null);
        var guide = new ExportedGuideImpl(index, json);
        var compiler = new WebPageCompiler(guide, new WebAssetsBundle(options), options,
                new WebResourceCopier(outputFolder), new SitePaths(guide, false));
        compiler.compile(PAGE_ID);

        return Files.readString(outputFolder.resolve("page.html"));
    }
}
