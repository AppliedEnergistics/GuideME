package guideme.render;

import guideme.color.ColorValue;
import guideme.color.LightDarkMode;
import guideme.document.LytRect;
import guideme.internal.GuideMEClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix3x2f;

public final class SimpleRenderContext implements RenderContext {
    private final List<LytRect> viewportStack = new ArrayList<>();
    private final GuiGraphics guiGraphics;
    private final LightDarkMode lightDarkMode;

    public SimpleRenderContext(
            LytRect viewport,
            GuiGraphics guiGraphics,
            LightDarkMode lightDarkMode) {
        this.viewportStack.add(viewport);
        this.guiGraphics = guiGraphics;
        this.lightDarkMode = lightDarkMode;
    }

    public SimpleRenderContext(LytRect viewport, GuiGraphics guiGraphics) {
        this(viewport, guiGraphics, GuideMEClient.currentLightDarkMode());
    }

    public SimpleRenderContext(GuiGraphics guiGraphics) {
        this(getDefaultViewport(), guiGraphics, GuideMEClient.currentLightDarkMode());
    }

    private static LytRect getDefaultViewport() {
        var width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        var height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return new LytRect(0, 0, width, height);
    }

    @Override
    public int resolveColor(ColorValue ref) {
        return ref.resolve(lightDarkMode);
    }

    @Override
    public void fillRect(LytRect rect, ColorValue topLeft, ColorValue topRight, ColorValue bottomRight,
            ColorValue bottomLeft) {

        guiGraphics.submitGuiElementRenderState(new GradientColoredRectangleRenderState(
                RenderPipelines.GUI,
                TextureSetup.noTexture(),
                new Matrix3x2f(poseStack()),
                rect.x(),
                rect.y(),
                rect.right(),
                rect.bottom(),
                resolveColor(topLeft),
                resolveColor(topRight),
                resolveColor(bottomRight),
                resolveColor(bottomLeft),
                guiGraphics().peekScissorStack()));
    }

    @Override
    public void fillTexturedRect(LytRect rect, ResourceLocation textureId, ColorValue topLeft, ColorValue topRight,
            ColorValue bottomRight, ColorValue bottomLeft, float u0, float v0, float u1, float v1) {

        var textureView = Minecraft.getInstance().getTextureManager().getTexture(textureId).getTextureView();
        guiGraphics.submitGuiElementRenderState(new GradientBlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(textureView),
                new Matrix3x2f(poseStack()),
                rect.x(),
                rect.y(),
                rect.right(),
                rect.bottom(),
                u0,
                v0,
                u1,
                v1,
                resolveColor(topLeft),
                resolveColor(topRight),
                resolveColor(bottomRight),
                resolveColor(bottomLeft),
                guiGraphics().peekScissorStack()));
    }

    @Override
    public void fillTriangle(Vec2 p1, Vec2 p2, Vec2 p3, ColorValue color) {
        guiGraphics.submitGuiElementRenderState(new FillTriangleRenderState(
                RenderPipelines.GUI,
                TextureSetup.noTexture(),
                new Matrix3x2f(poseStack()),
                p1,
                p2,
                p3,
                resolveColor(color),
                guiGraphics().peekScissorStack()));
    }

    @Override
    public void renderItem(ItemStack stack, int x, int y, int z, float width, float height) {
        var pose = poseStack();
        pose.pushMatrix();
        pose.translate(x, y);
        // Purposefully do NOT scale the normals!
        // this happens on non-uniform scales when calling the normal scale method
        pose.scale(width / 16, height / 16);
        guiGraphics().renderItem(stack, 0, 0);
        guiGraphics().renderItemDecorations(font(), stack, 0, 0);
        pose.popMatrix();
    }

    @Override
    public void pushScissor(LytRect bounds) {

        var rootBounds = bounds.transform(poseStack());

        viewportStack.add(rootBounds);
        RenderContext.super.pushScissor(bounds);
    }

    @Override
    public void popScissor() {
        if (viewportStack.size() <= 1) {
            throw new IllegalStateException("There is no active scissor rectangle.");
        }
        viewportStack.removeLast();
        RenderContext.super.popScissor();
    }

    @Override
    public LytRect viewport() {
        var viewport = viewportStack.getLast();
        var pose = new Matrix3x2f(guiGraphics().pose());
        pose.invert();
        var vp = viewport.transform(pose);
        return vp;
    }

    @Override
    public GuiGraphics guiGraphics() {
        return guiGraphics;
    }

    @Override
    public LightDarkMode lightDarkMode() {
        return lightDarkMode;
    }
}
