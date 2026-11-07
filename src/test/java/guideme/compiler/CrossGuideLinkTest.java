package guideme.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.Guide;
import guideme.GuidePage;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import guideme.document.flow.LytFlowLink;
import guideme.internal.GuideME;
import guideme.internal.GuideRegistry;
import guideme.internal.MutableGuide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class CrossGuideLinkTest {
    private static final String TARGET_GUIDE_ID = "crossguide:target";
    private static final String TARGET_PAGE_ID = "crossguide:other/page.md";

    private final List<MutableGuide> registeredGuides = new ArrayList<>();

    public CrossGuideLinkTest(MinecraftServer server) {
    }

    @AfterEach
    void unregisterGuides() {
        for (var guide : registeredGuides) {
            GuideRegistry.unregisterStatic(guide);
        }
        registeredGuides.clear();
    }

    @Test
    void linksToPageInOtherGuide() {
        registerGuide(TARGET_GUIDE_ID, Set.of(TARGET_PAGE_ID));

        var page = compile("[Other](guide:" + TARGET_GUIDE_ID + "#" + TARGET_PAGE_ID + ")");

        assertThat(findLinks(page)).hasSize(1);
        assertThat(page.document().getTextContent().trim()).isEqualTo("Other");
    }

    @Test
    void linksToPageByShorthandId() {
        registerGuide(TARGET_GUIDE_ID, Set.of("crossguide:page.md"));

        var page = compile("[Other](guide:" + TARGET_GUIDE_ID + "#page.md)");

        assertThat(findLinks(page)).hasSize(1);
    }

    @Test
    void rendersPlainTextIfGuideIsMissing() {
        var page = compile("[Other](guide:not_installed:guide#" + TARGET_PAGE_ID + ")");

        assertThat(findLinks(page)).isEmpty();
        assertThat(page.document().getTextContent().trim()).isEqualTo("Other");
    }

    @Test
    void rendersPlainTextIfPageIsMissing() {
        registerGuide(TARGET_GUIDE_ID, Set.of("crossguide:other/missing.md"));

        var page = compile("[Other](guide:" + TARGET_GUIDE_ID + "#" + TARGET_PAGE_ID + ")");

        assertThat(findLinks(page)).isEmpty();
        assertThat(page.document().getTextContent().trim()).isEqualTo("Other");
    }

    @Test
    void reportsMalformedGuideLink() {
        registerGuide(TARGET_GUIDE_ID, Set.of(TARGET_PAGE_ID));

        var page = compile("[Other](guide:" + TARGET_GUIDE_ID + ")");

        assertThat(findLinks(page)).isEmpty();
        assertThat(page.document().getTextContent()).contains("Expected guide:");
    }

    private void registerGuide(String guideId, Set<String> pageIds) {
        var guide = (MutableGuide) Guide.builder(Identifier.parse(guideId))
                .watchDevelopmentSources(false)
                .disableOpenHotkey()
                .build();

        var pages = new HashMap<Identifier, ParsedGuidePage>();
        for (var pageId : pageIds) {
            var id = Identifier.parse(pageId);
            pages.put(id, PageCompiler.parse(GuideME.MOD_ID, "en_us", id, "# Page\n\nContent\n"));
        }
        guide.setPages(pages);

        registeredGuides.add(guide);
    }

    private static GuidePage compile(String markdown) {
        var guide = (MutableGuide) Guide.builder(GuideME.makeId("source"))
                .watchDevelopmentSources(false)
                .register(false)
                .disableOpenHotkey()
                .build();
        var parsed = PageCompiler.parse(GuideME.MOD_ID, "en_us", GuideME.makeId("page.md"), markdown);
        return PageCompiler.compile(guide, guide.getExtensions(), parsed);
    }

    private static List<LytFlowLink> findLinks(GuidePage page) {
        var links = new ArrayList<LytFlowLink>();
        page.document().visit(new LytVisitor() {
            @Override
            public Result beforeFlowContent(LytFlowContent content) {
                if (content instanceof LytFlowLink link) {
                    links.add(link);
                }
                return Result.CONTINUE;
            }
        });
        return links;
    }
}
