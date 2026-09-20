package guideme.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Renders a 3d cross to visualize the alignment of the x, y, and z axes.
 * <p>
 * This used to build its own GPU vertex buffer and open its own {@code RenderPass} against
 * {@code Minecraft#getMainRenderTarget()}. Neither works any more:
 * <ul>
 * <li>{@code Minecraft#getMainRenderTarget()} moved to {@code GameRenderer#mainRenderTarget()}, and
 * {@code RenderSystem.outputColorTextureOverride}/{@code outputDepthTextureOverride} (which is how the
 * guidebook redirected rendering into the picture-in-picture texture) were removed entirely.</li>
 * <li>The scene is now drawn inside a render pass owned by {@code PictureInPictureRenderer}, whose color
 * and depth texture views are private, so we cannot open a second pass against the correct target.</li>
 * </ul>
 * The renderer therefore submits its lines through the submit-node pipeline like everything else, which
 * automatically puts them in the same render pass, with the same camera, as the rest of the scene.
 */
public final class AxisDebugRenderer {

    private static final float LENGTH = 25f;

    private AxisDebugRenderer() {
    }

    public static void render(SubmitNodeCollector collector, PoseStack poseStack) {
        // RenderPipelines.LINES now takes the line width as a per-vertex attribute
        // (DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH) instead of a global GL line width.
        var lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;

        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            axis(buffer, pose, LENGTH, 0, 0, 0xFFFF0000, lineWidth);
            axis(buffer, pose, 0, LENGTH, 0, 0xFF00FF00, lineWidth);
            axis(buffer, pose, 0, 0, LENGTH, 0xFF7F7FFF, lineWidth);
        });
    }

    private static void axis(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color,
            float lineWidth) {
        var nx = x != 0 ? 1f : 0f;
        var ny = y != 0 ? 1f : 0f;
        var nz = z != 0 ? 1f : 0f;
        buffer.addVertex(pose, 0f, 0f, 0f).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        buffer.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }
}
