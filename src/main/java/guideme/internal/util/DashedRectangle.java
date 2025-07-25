package guideme.internal.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import guideme.document.LytRect;
import guideme.render.RenderContext;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * Rendering helper for rendering a rectangle with a dashed outline.
 */
public final class DashedRectangle {
    private DashedRectangle() {
    }

    public static void render(RenderContext context, LytRect bounds, DashPattern pattern) {
        context.guiGraphics().submitGuiElementRenderState(new RenderState(
                new Matrix3x2f(context.poseStack()),
                bounds,
                pattern,
                context.guiGraphics().peekScissorStack()));
    }

    private static void buildHorizontalDashedLine(VertexConsumer builder, Matrix3x2f pose,
            float t, float x1, float x2, float y, float z,
            DashPattern pattern, boolean reverse) {
        if (!reverse) {
            t = 1 - t;
        }
        var phase = t * pattern.length();

        var color = pattern.color();

        for (float x = x1 - phase; x < x2; x += pattern.length()) {
            builder.addVertexWith2DPose(pose, Mth.clamp(x + pattern.onLength(), x1, x2), y, z).setColor(color);
            builder.addVertexWith2DPose(pose, Mth.clamp(x, x1, x2), y, z).setColor(color);
            builder.addVertexWith2DPose(pose, Mth.clamp(x, x1, x2), y + pattern.width(), z).setColor(color);
            builder.addVertexWith2DPose(pose, Mth.clamp(x + pattern.onLength(), x1, x2), y + pattern.width(), z)
                    .setColor(color);
        }
    }

    private static void buildVerticalDashedLine(VertexConsumer builder, Matrix3x2f pose,
            float t, float x, float y1, float y2, float z,
            DashPattern pattern, boolean reverse) {
        if (!reverse) {
            t = 1 - t;
        }
        var phase = t * pattern.length();

        var color = pattern.color();

        for (float y = y1 - phase; y < y2; y += pattern.length()) {
            builder.addVertexWith2DPose(pose, x + pattern.width(), Mth.clamp(y, y1, y2), z).setColor(color);
            builder.addVertexWith2DPose(pose, x, Mth.clamp(y, y1, y2), z).setColor(color);
            builder.addVertexWith2DPose(pose, x, Mth.clamp(y + pattern.onLength(), y1, y2), z).setColor(color);
            builder.addVertexWith2DPose(pose, x + pattern.width(), Mth.clamp(y + pattern.onLength(), y1, y2), z)
                    .setColor(color);
        }
    }

    public record RenderState(
            Matrix3x2f pose,
            LytRect documentBounds,
            DashPattern pattern,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        public RenderState(Matrix3x2f pose, LytRect documentBounds, DashPattern pattern,
                @Nullable ScreenRectangle scissorArea) {
            this(pose, documentBounds, pattern, scissorArea, getBounds(documentBounds, pose, scissorArea));
        }

        public void buildVertices(VertexConsumer vertices, float z) {
            var t = 0f;
            if (pattern.animationCycleMs() > 0) {
                t = (System.currentTimeMillis() % (int) pattern.animationCycleMs()) / pattern.animationCycleMs();
            }

            buildHorizontalDashedLine(vertices, pose, t, documentBounds.x(), documentBounds.right(), documentBounds.y(),
                    z, pattern, false);
            buildHorizontalDashedLine(vertices, pose, t, documentBounds.x(), documentBounds.right(),
                    documentBounds.bottom() - pattern.width(), z,
                    pattern, true);

            buildVerticalDashedLine(vertices, pose, t, documentBounds.x(), documentBounds.y(), documentBounds.bottom(),
                    z, pattern, true);
            buildVerticalDashedLine(vertices, pose, t, documentBounds.right() - pattern.width(), documentBounds.y(),
                    documentBounds.bottom(), z,
                    pattern, false);

        }

        public RenderPipeline pipeline() {
            return RenderPipelines.GUI_INVERT;
        }

        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Nullable
        private static ScreenRectangle getBounds(
                LytRect bounds, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
            var screenrectangle = bounds.toScreenRectangle().transformMaxBounds(pose);
            return scissorArea != null ? scissorArea.intersection(screenrectangle) : screenrectangle;
        }
    }

}
