package guideme.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Helper to build and draw a layer of sprites in a single draw-call.
 */
final class SpriteLayer {
    private final GuiGraphics graphics;
    private final List<Vertex> vertices = new ArrayList<>();
    private final ResourceLocation atlasLocation = AtlasIds.GUI;

    public SpriteLayer(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void addQuad(float x, float y, float width, float height, int color, float minU, float maxU,
            float minV, float maxV) {
        if (width < 0 || height < 0) {
            return;
        }

        vertices.add(new Vertex(x, y, minU, minV, color));
        vertices.add(new Vertex(x, y + height, minU, maxV, color));
        vertices.add(new Vertex(x + width, y + height, maxU, maxV, color));
        vertices.add(new Vertex(x + width, y, maxU, minV, color));
    }

    public void render(Matrix3x2fStack poseStack, int x, int y) {
        poseStack.pushMatrix();
        poseStack.translate(x, y);
        var scissor = graphics.peekScissorStack();
        var bounds = RenderState.getBounds(vertices, poseStack, scissor);
        var texture = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(atlasLocation).getTextureView();
        graphics.submitGuiElementRenderState(new RenderState(
                RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture), new Matrix3x2f(poseStack), vertices,
                scissor, bounds));
        poseStack.popMatrix();
    }

    record Vertex(float x, float y, float u, float v, int color) {
    }

    record RenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            List<Vertex> vertices,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vertices) {
            for (Vertex vertex : this.vertices) {
                vertices.addVertexWith2DPose(pose, vertex.x, vertex.y)
                        .setUv(vertex.u, vertex.v)
                        .setColor(vertex.color);
            }
        }

        @Nullable
        private static ScreenRectangle getBounds(
                List<Vertex> vertices, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
            var minX = (int) vertices.getFirst().x;
            var minY = (int) vertices.getFirst().y;
            var maxX = (int) Math.ceil(vertices.getFirst().x);
            var maxY = (int) Math.ceil(vertices.getFirst().y);
            for (Vertex vertex : vertices) {
                minX = Math.min(minX, (int) vertex.x);
                minY = Math.min(minY, (int) vertex.y);
                maxX = Math.max(maxX, (int) Math.ceil(vertex.x));
                maxY = Math.max(maxY, (int) Math.ceil(vertex.y));
            }

            ScreenRectangle screenrectangle = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY)
                    .transformMaxBounds(pose);
            return scissorArea != null ? scissorArea.intersection(screenrectangle) : screenrectangle;
        }
    }
}
