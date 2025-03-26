package guideme.render;

import guideme.color.ColorValue;
import guideme.color.LightDarkMode;
import guideme.document.LytRect;
import guideme.internal.GuideMEClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix4f;

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

        var buffers = beginBatch();
        var buffer = buffers.getBuffer(RenderType.gui());
        var matrix = poseStack().last().pose();
        final int z = 0;
        buffer.addVertex(matrix, rect.right(), rect.y(), z).setColor(resolveColor(topRight));
        buffer.addVertex(matrix, rect.x(), rect.y(), z).setColor(resolveColor(topLeft));
        buffer.addVertex(matrix, rect.x(), rect.bottom(), z).setColor(resolveColor(bottomLeft));
        buffer.addVertex(matrix, rect.right(), rect.bottom(), z).setColor(resolveColor(bottomRight));
        buffers.endBatch(RenderType.gui());
    }

    @Override
    public void fillTexturedRect(LytRect rect, ResourceLocation textureId, ColorValue topLeft, ColorValue topRight,
            ColorValue bottomRight, ColorValue bottomLeft, float u0, float v0, float u1, float v1) {
        RenderType renderType = RenderType.guiTextured(textureId);

        var buffers = beginBatch();
        var buffer = buffers.getBuffer(renderType);
        var matrix = poseStack().last().pose();
        final int z = 0;
        buffer.addVertex(matrix, rect.right(), rect.y(), z).setUv(u1, v0).setColor(resolveColor(topRight));
        buffer.addVertex(matrix, rect.x(), rect.y(), z).setUv(u0, v0).setColor(resolveColor(topLeft));
        buffer.addVertex(matrix, rect.x(), rect.bottom(), z).setUv(u0, v1).setColor(resolveColor(bottomLeft));
        buffer.addVertex(matrix, rect.right(), rect.bottom(), z).setUv(u1, v1).setColor(resolveColor(bottomRight));
        buffers.endBatch();
    }

    @Override
    public void fillTriangle(Vec2 p1, Vec2 p2, Vec2 p3, ColorValue color) {
        var resolvedColor = resolveColor(color);

        var buffers = beginBatch();
        var buffer = buffers.getBuffer(RenderType.gui());
        var matrix = poseStack().last().pose();
        final int z = 0;
        buffer.addVertex(matrix, p1.x, p1.y, z).setColor(resolvedColor);
        buffer.addVertex(matrix, p2.x, p2.y, z).setColor(resolvedColor);
        buffer.addVertex(matrix, p3.x, p3.y, z).setColor(resolvedColor);
        buffers.endBatch(RenderType.gui());
    }

    @Override
    public void renderItem(ItemStack stack, int x, int y, int z, float width, float height) {
        var mc = Minecraft.getInstance();

        var pose = poseStack();
        pose.pushPose();
        pose.translate(x, y, z + 1);
        // Purposefully do NOT scale the normals!
        // this happens on non-uniform scales when calling the normal scale method
        pose.last().pose().scale(width / 16, height / 16, Math.max(width / 16, height / 16));
        guiGraphics().renderItem(stack, 0, 0);
        guiGraphics().renderItemDecorations(mc.font, stack, 0, 0);
        pose.popPose();
    }

    @Override
    public void pushScissor(LytRect bounds) {

        var rootBounds = bounds.transform(poseStack().last().pose());

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
        var pose = new Matrix4f(guiGraphics().pose().last().pose());
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
