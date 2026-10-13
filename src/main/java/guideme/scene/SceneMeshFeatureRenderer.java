package guideme.scene;

import static guideme.scene.GuidebookLevelRenderer.getBlockRenderType;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import org.jetbrains.annotations.ApiStatus;

/**
 * Writes block and fluid geometry recorded by {@link SceneMeshCache} to the draws of the chunk layers, instead of
 * tessellating it again.
 */
@ApiStatus.Internal
public class SceneMeshFeatureRenderer extends RenderTypeFeatureRenderer<SceneMeshFeatureRenderer.Submit> {
    public static final FeatureRendererType<SceneMeshFeatureRenderer.Submit> TYPE = FeatureRendererType
            .create("guideme:SceneMesh");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
        // Like BlockModelFeatureRenderer: start the draws in the order Minecraft draws its chunk layers, so overlay
        // quads that are coplanar with the base quads are drawn after them. Draws that remain empty are skipped.
        getVertexBuilder(getBlockRenderType(ChunkSectionLayer.SOLID));
        getVertexBuilder(getBlockRenderType(ChunkSectionLayer.CUTOUT));

        for (var submit : submits) {
            for (var layer : submit.layers) {
                layer.geometry().replay(getVertexBuilder(getBlockRenderType(layer.layer())));
            }
        }
    }

    public record Layer(ChunkSectionLayer layer, RecordingVertexConsumer geometry) {
    }

    /**
     * @param pose   Only used for sorting translucent submits: transforms from the origin of the block the geometry
     *               belongs to. The geometry itself is already transformed.
     * @param layers The recorded geometry, by chunk layer.
     */
    public record Submit(PoseStack.Pose pose, List<Layer> layers) implements TranslucentSubmit {
        @Override
        public float distanceToCameraSq() {
            return TranslucentSubmit.computeDistanceToCameraSq(pose.pose(), 0.5f, 0.5f, 0.5f);
        }

        @Override
        public FeatureRendererType<Submit> featureType() {
            return TYPE;
        }
    }
}
