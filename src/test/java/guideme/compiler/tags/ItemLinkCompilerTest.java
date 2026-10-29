package guideme.compiler.tags;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.GuidePage;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class ItemLinkCompilerTest {

    public ItemLinkCompilerTest(MinecraftServer server) {
    }

    @Test
    void testMissingTargetPageFromSameNamespace() {
        var page = compilePage("minecraft:test_page", """
                <ItemLink id="minecraft:stick" />
                """);

        assertThat(page.document().getTextContent()).contains("No page found for item minecraft:stick");
    }

    @Test
    void testMissingTargetPageFromSameNamespaceButOptional() {
        var page = compilePage("minecraft:test_page", """
                <ItemLink id="minecraft:stick" optional />
                """);

        assertThat(page.document().getTextContent()).isEqualTo("Stick");
    }

    @Test
    void testMissingTargetPageFromOtherNamespace() {
        var page = compilePage("guideme:test_page", """
                <ItemLink id="minecraft:stick" />
                """);

        assertThat(page.document().getTextContent()).isEqualTo("Stick");
    }

    @Test
    void testMissingTargetPageFromOtherNamespaceButExplicitlyNonOptional() {
        var page = compilePage("guideme:test_page", """
                <ItemLink id="minecraft:stick" optional={false} />
                """);

        assertThat(page.document().getTextContent()).contains("No page found for item minecraft:stick");
    }

    private static GuidePage compilePage(String pageId, String content) {
        return TestPages.compile(pageId, content, new ItemLinkCompiler());
    }
}
