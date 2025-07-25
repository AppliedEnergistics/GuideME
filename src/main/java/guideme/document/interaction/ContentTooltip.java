package guideme.document.interaction;

import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.render.SimpleRenderContext;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link GuideTooltip} that renders a {@link LytBlock} as the tooltip content.
 */
public class ContentTooltip implements GuideTooltip {
    private final List<ClientTooltipComponent> components;

    // The window size for which we performed layout
    @Nullable
    private LytRect layoutViewport;
    @Nullable
    private LytRect layoutBox;

    private final LytBlock content;

    public ContentTooltip(LytBlock content) {
        this.content = content;

        this.components = List.of(
                new ClientTooltipComponent() {
                    @Override
                    public int getHeight(Font font) {
                        return getLayoutBox().height();
                    }

                    @Override
                    public int getWidth(Font font) {
                        return getLayoutBox().width();
                    }

                    @Override
                    public void renderText(GuiGraphics guiGraphics, Font font, int x, int y) {
                        getLayoutBox(); // Updates layout

                        var pose = guiGraphics.pose();
                        pose.pushMatrix();
                        pose.translate(x, y);

                        var ctx = new SimpleRenderContext(layoutViewport, guiGraphics);
                        content.render(ctx);

                        pose.popMatrix();
                    }

                    @Override
                    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics guiGraphics) {
                        getLayoutBox(); // Updates layout

                        var pose = guiGraphics.pose();
                        pose.pushMatrix();
                        pose.translate(x, y);
                        var ctx = new SimpleRenderContext(layoutViewport, guiGraphics);
                        content.render(ctx);
                        pose.popMatrix();
                    }
                });
    }

    @Override
    public List<ClientTooltipComponent> getLines() {
        return components;
    }

    public LytBlock getContent() {
        return content;
    }

    private LytRect getLayoutBox() {
        var screen = Minecraft.getInstance().screen;
        var currentViewport = new LytRect(0, 0, screen.width, screen.height);
        if (layoutBox == null || !currentViewport.equals(layoutViewport)) {
            layoutViewport = currentViewport;
            var layoutContext = new LayoutContext(new MinecraftFontMetrics());
            layoutBox = content.layout(layoutContext, 0, 0, screen.width / 2);
        }
        return layoutBox;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        ExportableResourceProvider.visit(content, exporter);
    }
}
