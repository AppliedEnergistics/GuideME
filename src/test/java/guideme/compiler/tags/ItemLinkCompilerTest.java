package guideme.compiler.tags;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.Guide;
import guideme.GuidePage;
import guideme.compiler.PageCompiler;
import guideme.compiler.TagCompiler;
import guideme.extensions.ExtensionCollection;
import guideme.internal.GuideME;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class ItemLinkCompilerTest {

    public ItemLinkCompilerTest(MinecraftServer server) {
    }

    @Test
    void testMissingTargetPageFromSameNamespace() throws IOException {
        var page = compilePage("minecraft:test_page", """
                <ItemLink id="minecraft:stick" />
                """);

        assertThat(page.document().getTextContent()).contains("No page found for item minecraft:stick");
    }

    @Test
    void testMissingTargetPageFromSameNamespaceButOptional() throws IOException {
        var page = compilePage("minecraft:test_page", """
                <ItemLink id="minecraft:stick" optional />
                """);

        assertThat(page.document().getTextContent()).isEqualTo("Stick");
    }

    @Test
    void testMissingTargetPageFromOtherNamespace() throws IOException {
        var page = compilePage("giudeme:test_page", """
                <ItemLink id="minecraft:stick" />
                """);

        assertThat(page.document().getTextContent()).isEqualTo("Stick");
    }

    @Test
    void testMissingTargetPageFromOtherNamespaceButExplicitlyNonOptional() throws IOException {
        var page = compilePage("giudeme:test_page", """
                <ItemLink id="minecraft:stick" optional={false} />
                """);

        assertThat(page.document().getTextContent()).contains("No page found for item minecraft:stick");
    }

    private static GuidePage compilePage(String pageId, String content) throws IOException {
        try (var in = new ByteArrayInputStream(content.getBytes())) {
            var parsed = PageCompiler.parse(GuideME.MOD_ID, "en_us", Identifier.parse(pageId), in);
            var testPages = Guide.builder(Identifier.fromNamespaceAndPath(GuideME.MOD_ID, "test"))
                    .watchDevelopmentSources(false)
                    .register(false)
                    .disableOpenHotkey()
                    .build();
            return PageCompiler.compile(testPages, ExtensionCollection.builder()
                    .add(TagCompiler.EXTENSION_POINT, new ItemLinkCompiler())
                    .build(), parsed);
        }
    }
}
