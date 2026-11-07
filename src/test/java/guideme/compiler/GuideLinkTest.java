package guideme.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import guideme.Guide;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuideLinkTest {

    @Test
    void parsesGuideAndPage() {
        var link = GuideLink.parse("guide:ae2:guide#getting-started.md");

        assertThat(link.guideId()).isEqualTo(Identifier.parse("ae2:guide"));
        assertThat(link.pageText()).isEqualTo("getting-started.md");
        assertThat(link.anchor()).isNull();
    }

    @Test
    void parsesAnchor() {
        var link = GuideLink.parse("guide:ae2:guide#getting-started.md#usage");

        assertThat(link.guideId()).isEqualTo(Identifier.parse("ae2:guide"));
        assertThat(link.pageText()).isEqualTo("getting-started.md");
        assertThat(link.anchor()).isEqualTo("usage");
    }

    @Test
    void ignoresEmptyAnchor() {
        var link = GuideLink.parse("guide:ae2:guide#getting-started.md#");

        assertThat(link.anchor()).isNull();
    }

    @Test
    void resolvesPageAgainstGuideNamespace() {
        var guide = mock(Guide.class);
        when(guide.getDefaultNamespace()).thenReturn("ae2");

        assertThat(GuideLink.parse("guide:ae2:guide#getting-started.md").resolvePageId(guide))
                .isEqualTo(Identifier.parse("ae2:getting-started.md"));
        assertThat(GuideLink.parse("guide:ae2:guide#other:getting-started.md").resolvePageId(guide))
                .isEqualTo(Identifier.parse("other:getting-started.md"));
    }

    @Test
    void detectsGuideLinks() {
        assertThat(GuideLink.isGuideLink("guide:ae2:guide#getting-started.md")).isTrue();
        assertThat(GuideLink.isGuideLink("./getting-started.md")).isFalse();
        assertThat(GuideLink.isGuideLink("https://example.com")).isFalse();
        assertThat(GuideLink.isGuideLink(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "getting-started.md",
            "guide:ae2:guide",
            "guide:guide#getting-started.md",
            "guide:ae2:guide#",
            "guide:ae2:gu:ide#getting-started.md"
    })
    void rejectsMalformedLinks(String href) {
        assertThatThrownBy(() -> GuideLink.parse(href))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
