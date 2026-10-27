package guideme.internal.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.blaze3d.Blaze3D;
import guideme.Guide;
import guideme.color.ColorValue;
import guideme.document.LytRect;
import guideme.layout.MinecraftFontMetrics;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
import guideme.render.GuiSprite;
import guideme.render.SimpleRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class GuideNavBarTest {
    @Test
    void expandsNestedLevelsInNavigationOrderAndNavigatesToNestedPages() {
        var grandchild = node("G", true);
        var root = node("R", true, 2, node("C", true, grandchild), node("S", true));

        try (var fixture = new NavBarFixture(List.of(root, node("O", true)))) {
            assertThat(fixture.titles()).containsExactly("R", "O");
            fixture.click("R");
            assertThat(fixture.titles()).containsExactly("R", "C", "S", "O");
            fixture.click("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "S", "O");

            fixture.click("G");
            verify(fixture.screen).navigateTo(grandchild.pageId());
        }
    }

    @Test
    void labelClickNavigatesAndExpandsButIndicatorOnlyToggles() {
        var root = node("R", false, node("C", false));

        try (var fixture = new NavBarFixture(List.of(root))) {
            fixture.click("R");
            assertThat(fixture.titles()).containsExactly("R", "C");
            verify(fixture.screen).navigateTo(root.pageId());

            // Clicking the label of an expanded group does not collapse it
            fixture.click("R");
            assertThat(fixture.titles()).containsExactly("R", "C");
            verify(fixture.screen, times(2)).navigateTo(root.pageId());

            fixture.clickIndicator("R");
            assertThat(fixture.titles()).containsExactly("R");
            fixture.clickIndicator("R");
            assertThat(fixture.titles()).containsExactly("R", "C");
            verify(fixture.screen, times(2)).navigateTo(any(Identifier.class));
        }
    }

    @Test
    void collapsesAllDescendantsAndPreservesTheirExpansionWhenReopened() {
        var root = node("R", 2, node("C", false, node("G", false)));

        try (var fixture = new NavBarFixture(List.of(root, node("O", false)))) {
            fixture.clickIndicator("R");
            fixture.clickIndicator("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "O");

            fixture.clickIndicator("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "O");
            assertThat(fixture.text("O").y()).isEqualTo(20);

            fixture.clickIndicator("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "O");
            fixture.clickIndicator("R");
            assertThat(fixture.titles()).containsExactly("R", "O");
            assertThat(fixture.text("O").y()).isEqualTo(10);

            fixture.clickIndicator("R");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "O");
            assertThat(fixture.text("O").y()).isEqualTo(30);
            verify(fixture.screen, never()).navigateTo(any(Identifier.class));
        }
    }

    @Test
    void hidesPagesBeyondTheMaximumDepth() {
        var hidden = node("P2", false, node("P3", false));
        var root = node("P0", false, node("P1", false, hidden), node("S", false));

        try (var fixture = new NavBarFixture(List.of(root), hidden.pageId())) {
            // The current page is not in the sidebar, so nothing is revealed or highlighted
            assertThat(fixture.titles()).containsExactly("P0");
            assertThat(fixture.filledRects).isEmpty();

            fixture.clickIndicator("P0");
            assertThat(fixture.titles()).containsExactly("P0", "P1", "S");
            assertThat(fixture.text("P1").x()).isEqualTo((GuideNavBar.DEFAULT_MAX_DEPTH - 1) * 10 + 8 + 5);
            // Only P0 is expandable
            assertThat(fixture.indicatorCount()).isEqualTo(1);
        }
    }

    @Test
    void pageCanShowMoreLevelsBelowItThanTheDefault() {
        var root = node("P0", false, node("P1", 3, node("P2", false, node("P3", false,
                node("P4", false, node("P5", false))))));

        try (var fixture = new NavBarFixture(List.of(root))) {
            for (int i = 0; i < 4; i++) {
                fixture.clickIndicator("P" + i);
            }
            // P1 shows three levels below it, so P5 on the fourth level is hidden
            assertThat(fixture.titles()).containsExactly("P0", "P1", "P2", "P3", "P4");
            assertThat(fixture.indicatorCount()).isEqualTo(4);
        }
    }

    @Test
    void pageCanShowFewerLevelsBelowItThanTheDefault() {
        var root = node("P0", 0, node("P1", false));

        try (var fixture = new NavBarFixture(List.of(root, node("O", false, node("C", false))))) {
            fixture.clickIndicator("O");
            assertThat(fixture.titles()).containsExactly("P0", "O", "C");
            // P0 has max depth 0, so only O is expandable
            assertThat(fixture.indicatorCount()).isEqualTo(1);
        }
    }

    @Test
    void nestedMaxDepthOverridesTheInheritedDepth() {
        var root = node("P0", 1, node("P1", 2, node("P2", false, node("P3", false))));

        try (var fixture = new NavBarFixture(List.of(root))) {
            fixture.clickIndicator("P0");
            fixture.clickIndicator("P1");
            fixture.clickIndicator("P2");
            assertThat(fixture.titles()).containsExactly("P0", "P1", "P2", "P3");
        }
    }

    @Test
    void revealsAndHighlightsTheCurrentPage() {
        var current = node("G", false);
        var root = node("R", 2, node("C", false, current));

        try (var fixture = new NavBarFixture(List.of(root, node("O", false)), current.pageId())) {
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "O");
            var row = fixture.text("G");
            assertThat(fixture.filledRects).containsExactly(
                    new LytRect(20, (int) row.y(), GuideNavBar.WIDTH_OPEN - 20, 10));

            // The user can still collapse the ancestors of the current page
            fixture.clickIndicator("R");
            assertThat(fixture.titles()).containsExactly("R", "O");
            assertThat(fixture.filledRects).isEmpty();
        }
    }

    @Test
    void drawsAChevronPointingRightWhenCollapsedAndDownWhenExpanded() {
        try (var fixture = new NavBarFixture(List.of(node("R", false, node("C", false))))) {
            var rowY = (int) fixture.text("R").y();
            assertThat(fixture.indicator("R"))
                    .isEqualTo(new IndicatorDraw(5, rowY, GuideNavBar.CHEVRON_COLLAPSED));

            fixture.clickIndicator("R");
            assertThat(fixture.indicator("R"))
                    .isEqualTo(new IndicatorDraw(3, rowY + 2, GuideNavBar.CHEVRON_EXPANDED));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0, false", "0, true",
            "1, false", "1, true"
    })
    void alignsPageAndGroupTextIconsAndIndicatorsAtEveryDepth(int depth, boolean hasIcon) {
        List<NavigationNode> nodes = List.of(node("L", hasIcon), node("G", hasIcon, node("C", false)));
        for (int i = depth - 1; i >= 0; i--) {
            // The outermost level shows enough levels for the nodes under test to be expandable
            nodes = List.of(node("P" + i, false, i == 0 ? depth + 1 : null, nodes.toArray(NavigationNode[]::new)));
        }

        try (var fixture = new NavBarFixture(nodes)) {
            for (int i = 0; i < depth; i++) {
                fixture.clickIndicator("P" + i);
            }

            int expectedTextX = depth * 10 + 8 + (hasIcon ? 8 : 0) + 5;
            assertThat(fixture.text("L").x()).isEqualTo(expectedTextX);
            assertThat(fixture.text("G").x()).isEqualTo(expectedTextX);
            assertThat(fixture.indicatorX("G")).isEqualTo(depth * 10 + 5);
            if (hasIcon) {
                assertThat(fixture.iconX("L")).isEqualTo(depth * 10 + 12);
                assertThat(fixture.iconX("G")).isEqualTo(depth * 10 + 12);
            }

            var page = fixture.text("L");
            fixture.render((int) page.x(), (int) page.y() + 1);
            assertThat(fixture.filledRects).containsExactly(
                    new LytRect(depth * 10, (int) page.y(), GuideNavBar.WIDTH_OPEN - depth * 10, 10));
        }
    }

    @Test
    void rendersNoRowsForAnEmptyNavigationTree() {
        try (var fixture = new NavBarFixture(List.of())) {
            assertThat(fixture.titles()).isEmpty();
            assertThat(fixture.navBar.active).isFalse();
        }
    }

    private static NavigationNode node(String title, boolean hasIcon, NavigationNode... children) {
        return node(title, hasIcon, null, children);
    }

    private static NavigationNode node(String title, int maxDepth, NavigationNode... children) {
        return node(title, false, maxDepth, children);
    }

    private static NavigationNode node(String title, boolean hasIcon, @Nullable Integer maxDepth,
            NavigationNode... children) {
        return new NavigationNode(Identifier.fromNamespaceAndPath("testmod", title.toLowerCase(Locale.ROOT)),
                title, null, hasIcon ? Lazy.of(() -> ItemStack.EMPTY) : null,
                List.of(children), 0, true, maxDepth);
    }

    private record TextDraw(String text, float x, float y) {
    }

    private record IconDraw(int x, int y) {
    }

    private record IndicatorDraw(int x, int y, GuiSprite sprite) {
    }

    /**
     * Exercise the real widget's layout and input while recording draw calls without an OpenGL context.
     */
    private static class NavBarFixture implements AutoCloseable {
        private final GuideScreen screen = mock(GuideScreen.class);
        private final GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        private final List<TextDraw> textDraws = new ArrayList<>();
        private final List<IconDraw> iconDraws = new ArrayList<>();
        private final List<IndicatorDraw> indicatorDraws = new ArrayList<>();
        private final List<LytRect> filledRects = new ArrayList<>();
        private final MockedStatic<Minecraft> minecraft;
        private final MockedStatic<Blaze3D> blaze3d;
        private final MockedConstruction<MinecraftFontMetrics> fonts;
        private final MockedConstruction<SimpleRenderContext> contexts;
        private final GuideNavBar navBar;

        private NavBarFixture(List<NavigationNode> nodes) {
            this(nodes, null);
        }

        private NavBarFixture(List<NavigationNode> nodes, @Nullable Identifier currentPageId) {
            var client = mock(Minecraft.class);
            when(client.getSoundManager()).thenReturn(mock(SoundManager.class));
            minecraft = mockStatic(Minecraft.class);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            blaze3d = mockStatic(Blaze3D.class);
            fonts = mockConstruction(MinecraftFontMetrics.class, (font, context) -> {
                when(font.getAdvance(anyInt(), any())).thenReturn(10f);
                when(font.getLineHeight(any())).thenReturn(10);
            });
            contexts = mockConstruction(SimpleRenderContext.class, (context, construction) -> {
                when(context.viewport()).thenReturn(new LytRect(0, 0, GuideNavBar.WIDTH_OPEN, 400));
                when(context.intersectsViewport(any())).thenReturn(true);
                doAnswer(call -> {
                    textDraws.add(new TextDraw(call.getArgument(0), call.getArgument(2), call.getArgument(3)));
                    return null;
                }).when(context).renderText(anyString(), any(), anyFloat(), anyFloat());
                doAnswer(call -> {
                    iconDraws.add(new IconDraw(call.getArgument(1), call.getArgument(2)));
                    return null;
                }).when(context).renderItem(any(), anyInt(), anyInt(), anyInt(), anyFloat(), anyFloat());
                doAnswer(call -> {
                    indicatorDraws
                            .add(new IndicatorDraw(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
                    return null;
                }).when(context).drawIcon(anyInt(), anyInt(), any(GuiSprite.class), any(ColorValue.class));
                doAnswer(call -> {
                    filledRects.add(call.getArgument(0));
                    return null;
                }).when(context).fillRect(any(LytRect.class), any(ColorValue.class));
            });

            var guide = mock(Guide.class);
            when(guide.getNavigationTree()).thenReturn(new NavigationTree(Map.of(), nodes));
            when(screen.getGuide()).thenReturn(guide);
            when(screen.getCurrentPageId()).thenReturn(currentPageId);
            screen.height = 400;
            when(graphics.pose()).thenReturn(new Matrix3x2fStack(16));
            navBar = new GuideNavBar(screen);
            navBar.setPinned(true);
            render(-1, -1);
        }

        private void render(int mouseX, int mouseY) {
            textDraws.clear();
            iconDraws.clear();
            indicatorDraws.clear();
            filledRects.clear();
            navBar.extractWidgetRenderState(graphics, mouseX, mouseY, 0);
        }

        private List<String> titles() {
            return textDraws.stream().map(TextDraw::text).toList();
        }

        private TextDraw text(String title) {
            return textDraws.stream().filter(draw -> draw.text().equals(title)).findFirst().orElseThrow();
        }

        private int iconX(String title) {
            float y = text(title).y();
            return iconDraws.stream().filter(draw -> draw.y() == y).findFirst().orElseThrow().x();
        }

        private int indicatorCount() {
            return indicatorDraws.size();
        }

        /**
         * The expand indicator drawn in the row of the given page.
         */
        private IndicatorDraw indicator(String title) {
            float rowY = text(title).y();
            return indicatorDraws.stream().filter(draw -> draw.y() >= rowY && draw.y() < rowY + 10).findFirst()
                    .orElseThrow();
        }

        private int indicatorX(String title) {
            return indicator(title).x();
        }

        private void click(String title) {
            var text = text(title);
            leftClick(text.x(), text.y() + 1);
            render(-1, -1);
        }

        private void clickIndicator(String title) {
            var y = text(title).y();
            leftClick(indicatorX(title) + 2, y + 1);
            render(-1, -1);
        }

        private void leftClick(double x, double y) {
            navBar.onClick(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
        }

        @Override
        public void close() {
            contexts.close();
            fonts.close();
            blaze3d.close();
            minecraft.close();
        }
    }
}
