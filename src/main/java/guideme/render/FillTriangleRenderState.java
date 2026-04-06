package guideme.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

record FillTriangleRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        Vector2f p1,
        Vector2f p2,
        Vector2f p3,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    public FillTriangleRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            Vector2f p1,
            Vector2f p2,
            Vector2f p3,
            int color,
            @Nullable ScreenRectangle scissorArea) {
        this(
                pipeline,
                textureSetup,
                pose,
                p1,
                p2,
                p3,
                color,
                scissorArea,
                getBounds(p1, p2, p3, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(pose, p1.x, p1.y).setColor(color);
        vertices.addVertexWith2DPose(pose, p2.x, p2.y).setColor(color);
        vertices.addVertexWith2DPose(pose, p3.x, p3.y).setColor(color);
        // NOTE: GuiElementRenderState can only render quads, and as such we're building a degenerate quad here to get a
        // triangle
        vertices.addVertexWith2DPose(pose, p3.x, p3.y).setColor(color);
    }

    @Nullable
    private static ScreenRectangle getBounds(
            Vector2f p1, Vector2f p2, Vector2f p3, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        var x0 = (int) Math.min(Math.min(p1.x, p2.x), p3.x);
        var x1 = (int) Math.ceil(Math.max(Math.max(p1.x, p2.x), p3.x));
        var y0 = (int) Math.min(Math.min(p1.y, p2.y), p3.y);
        var y1 = (int) Math.ceil(Math.max(Math.max(p1.y, p2.y), p3.y));

        ScreenRectangle screenrectangle = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(screenrectangle) : screenrectangle;
    }
}
