package guideme.internal;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.Guide;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import guideme.navigation.NavigationNode;
import java.util.HashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class MutableGuideTest {

    public MutableGuideTest(MinecraftServer server) {
    }

    @Test
    void hidesPageIfRequiredModIsMissing() {
        var visible = parsePage("visible", "Hello");
        var hidden = parsePage("hidden", "requires a mod that is not installed", "not_installed");

        var guide = buildGuide(visible, hidden);

        assertThat(guide.getPages()).extracting(ParsedGuidePage::getId)
                .containsExactly(visible.getId());
        assertThat(guide.pageExists(hidden.getId())).isFalse();
        assertThat(guide.getParsedPage(hidden.getId())).isNull();
    }

    @Test
    void keepsPageIfRequiredModIsPresent() {
        var page = parsePage("page", "requires guideme", "guideme");

        var guide = buildGuide(page);

        assertThat(guide.getPages()).extracting(ParsedGuidePage::getId)
                .containsExactly(page.getId());
    }

    @Test
    void hidesPageIfInvertedRequiredModIsPresent() {
        var page = parsePage("page", "requires guideme to be absent", "!guideme");

        var guide = buildGuide(page);

        assertThat(guide.getPages()).isEmpty();
    }

    @Test
    void hidesPageIfAnyRequiredModIsMissing() {
        var page = parsePage("page", "requires both", "guideme", "not_installed");

        var guide = buildGuide(page);

        assertThat(guide.getPages()).isEmpty();
    }

    @Test
    void hiddenPagesAreNotPartOfTheNavigationTree() {
        var visible = parsePage("visible", "Hello", "guideme");
        var hidden = parsePage("hidden", "Hidden", "not_installed");

        var guide = buildGuide(visible, hidden);

        assertThat(guide.getNavigationTree().getRootNodes())
                .extracting(NavigationNode::pageId)
                .containsExactly(visible.getId());
        assertThat(guide.getNavigationTree().getNodeById(hidden.getId())).isNull();
    }

    private static MutableGuide buildGuide(ParsedGuidePage... pages) {
        var guide = (MutableGuide) Guide.builder(GuideME.makeId("test"))
                .watchDevelopmentSources(false)
                .register(false)
                .disableOpenHotkey()
                .build();

        var pageMap = new HashMap<Identifier, ParsedGuidePage>();
        for (var page : pages) {
            pageMap.put(page.getId(), page);
        }
        guide.setPages(pageMap);
        return guide;
    }

    private static ParsedGuidePage parsePage(String path, String content, String... requiredMods) {
        var frontmatter = new StringBuilder();
        if (requiredMods.length > 0) {
            frontmatter.append("required_mods:\n");
            for (var modId : requiredMods) {
                frontmatter.append("  - \"").append(modId).append("\"\n");
            }
        }
        frontmatter.append("navigation:\n  title: ").append(path).append("\n");

        var markdown = "---\n" + frontmatter + "---\n\n# " + path + "\n\n" + content + "\n";

        var pageId = GuideME.makeId(path + ".md");
        return PageCompiler.parse(GuideME.MOD_ID, "en_us", pageId, markdown);
    }
}
