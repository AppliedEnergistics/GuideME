package guideme.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * Just an extension of {@link net.minecraft.client.gui.render.state.ColoredRectangleRenderState} that allows for all
 * four corners of the rectangle to have a different color, not just the top/bottom.
 */
record GradientColoredRectangleRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        int x0,
        int y0,
        int x1,
        int y1,
        int colTL,
        int colTR,
        int colBR,
        int colBL,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    public GradientColoredRectangleRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            int x0,
            int y0,
            int x1,
            int y1,
            int colUL,
            int colTR,
            int colBR,
            int colBL,
            @Nullable ScreenRectangle scissorArea) {
        this(
                pipeline,
                textureSetup,
                pose,
                x0,
                y0,
                x1,
                y1,
                colUL,
                colTR,
                colBR,
                colBL,
                scissorArea,
                getBounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(pose, x0, y0).setColor(colTL);
        vertices.addVertexWith2DPose(pose, x0, y1).setColor(colBL);
        vertices.addVertexWith2DPose(pose, x1, y1).setColor(colBR);
        vertices.addVertexWith2DPose(pose, x1, y0).setColor(colTR);
    }

    @Nullable
    private static ScreenRectangle getBounds(
            int x0, int y0, int x1, int y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle screenrectangle = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(screenrectangle) : screenrectangle;
    }
}
