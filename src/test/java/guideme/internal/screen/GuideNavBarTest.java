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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.blaze3d.Blaze3D;
import guideme.Guide;
import guideme.color.ColorValue;
import guideme.document.LytRect;
import guideme.layout.MinecraftFontMetrics;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
import guideme.render.SimpleRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.Lazy;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class GuideNavBarTest {
    @Test
    void expandsAllLevelsInNavigationOrderAndNavigatesToNestedPages() {
        var deep = node("D", true);
        var root = node("R", true, node("C", true, node("G", true, deep)), node("S", true));

        try (var fixture = new NavBarFixture(List.of(root, node("O", true)))) {
            assertThat(fixture.titles()).containsExactly("R", "O");
            fixture.click("R");
            assertThat(fixture.titles()).containsExactly("R", "C", "S", "O");
            fixture.click("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "S", "O");
            fixture.click("G");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "D", "S", "O");

            fixture.click("D");
            verify(fixture.screen).navigateTo(deep.pageId());
        }
    }

    @Test
    void collapsesAllDescendantsAndPreservesTheirExpansionWhenReopened() {
        var root = node("R", false, node("C", false, node("G", false, node("D", false))));

        try (var fixture = new NavBarFixture(List.of(root, node("O", false)))) {
            fixture.click("R");
            fixture.click("C");
            fixture.click("G");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "D", "O");

            fixture.click("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "O");
            assertThat(fixture.text("O").y()).isEqualTo(20);

            fixture.click("C");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "D", "O");
            fixture.click("R");
            assertThat(fixture.titles()).containsExactly("R", "O");
            assertThat(fixture.text("O").y()).isEqualTo(10);

            fixture.click("R");
            assertThat(fixture.titles()).containsExactly("R", "C", "G", "D", "O");
            assertThat(fixture.text("O").y()).isEqualTo(40);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0, false", "0, true",
            "1, false", "1, true",
            "2, false", "2, true",
            "3, false", "3, true",
            "8, false", "8, true"
    })
    void alignsPageAndGroupTextIconsAndIndicatorsAtEveryDepth(int depth, boolean hasIcon) {
        List<NavigationNode> nodes = List.of(node("L", hasIcon), node("G", hasIcon, node("C", false)));
        for (int i = depth - 1; i >= 0; i--) {
            nodes = List.of(node("P" + i, false, nodes.toArray(NavigationNode[]::new)));
        }

        try (var fixture = new NavBarFixture(nodes)) {
            for (int i = 0; i < depth; i++) {
                fixture.click("P" + i);
            }

            int expectedTextX = depth * 10 + 7 + (hasIcon ? 8 : 0) + 5;
            assertThat(fixture.text("L").x()).isEqualTo(expectedTextX);
            assertThat(fixture.text("G").x()).isEqualTo(expectedTextX);
            assertThat(fixture.indicatorX("G")).isEqualTo(depth * 10 + 5);
            if (hasIcon) {
                assertThat(fixture.iconX("L")).isEqualTo(depth * 10 + 11);
                assertThat(fixture.iconX("G")).isEqualTo(depth * 10 + 11);
            }

            var page = fixture.text("L");
            fixture.render((int) page.x(), (int) page.y() + 1);
            assertThat(fixture.hoverBounds).containsExactly(
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
        return new NavigationNode(Identifier.fromNamespaceAndPath("testmod", title.toLowerCase(Locale.ROOT)),
                title, null, hasIcon ? Lazy.of(() -> new ItemStack(Items.STONE)) : null,
                List.of(children), 0, true);
    }

    private record TextDraw(String text, float x, float y) {
    }

    private record IconDraw(int x, int y) {
    }

    private record IndicatorDraw(float x, float y) {
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
        private final List<LytRect> hoverBounds = new ArrayList<>();
        private final MockedStatic<Minecraft> minecraft;
        private final MockedStatic<Blaze3D> blaze3d;
        private final MockedConstruction<MinecraftFontMetrics> fonts;
        private final MockedConstruction<SimpleRenderContext> contexts;
        private final GuideNavBar navBar;

        private NavBarFixture(List<NavigationNode> nodes) {
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
                    Vector2f point = call.getArgument(1);
                    indicatorDraws.add(new IndicatorDraw(point.x, point.y));
                    return null;
                }).when(context).fillTriangle(any(), any(), any(), any());
                doAnswer(call -> {
                    hoverBounds.add(call.getArgument(0));
                    return null;
                }).when(context).fillRect(any(LytRect.class), any(ColorValue.class));
            });

            var guide = mock(Guide.class);
            when(guide.getNavigationTree()).thenReturn(new NavigationTree(Map.of(), nodes));
            when(screen.getGuide()).thenReturn(guide);
            screen.height = 400;
            when(graphics.pose()).thenReturn(new Matrix3x2fStack(8));
            navBar = new GuideNavBar(screen);
            navBar.setPinned(true);
            render(-1, -1);
        }

        private void render(int mouseX, int mouseY) {
            textDraws.clear();
            iconDraws.clear();
            indicatorDraws.clear();
            hoverBounds.clear();
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

        private float indicatorX(String title) {
            float y = text(title).y() + 2;
            return indicatorDraws.stream().filter(draw -> draw.y() == y).findFirst().orElseThrow().x();
        }

        private void click(String title) {
            var text = text(title);
            var event = mock(MouseButtonEvent.class);
            when(event.x()).thenReturn((double) text.x());
            when(event.y()).thenReturn((double) text.y() + 1);
            navBar.onClick(event, false);
            render(-1, -1);
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
