package guideme.scene;

import static guideme.scene.GuidebookLevelRenderer.getBlockRenderType;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4fc;

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
        // Draws are ordered by first use of their render type, and geometry of the same render type is merged into
        // one draw. Models often have overlay quads (i.e. emissive parts) that are coplanar with the base quads and
        // only show up if they're drawn after them. Start the draws in the order Minecraft draws its chunk layers.
        // Draws that remain empty are skipped.
        getVertexBuilder(getBlockRenderType(ChunkSectionLayer.SOLID));
        getVertexBuilder(getBlockRenderType(ChunkSectionLayer.CUTOUT));

        for (var submit : submits) {
            var identity = (submit.pose.pose().properties() & Matrix4fc.PROPERTY_IDENTITY) != 0;
            for (var layer : submit.layers) {
                VertexConsumer consumer = getVertexBuilder(getBlockRenderType(layer.layer()));
                if (!identity) {
                    consumer = new LiquidVertexConsumer(consumer, submit.pose);
                }
                layer.geometry().replay(consumer);
            }
        }
    }

    public record Layer(ChunkSectionLayer layer, RecordingVertexConsumer geometry) {
    }

    /**
     * @param pose    Transforms from level coordinates, which the geometry was recorded in.
     * @param sortPos The block that translucent geometry is sorted by.
     * @param layers  The recorded geometry, by chunk layer.
     */
    public record Submit(PoseStack.Pose pose, BlockPos sortPos, List<Layer> layers) implements TranslucentSubmit {
        @Override
        public float distanceToCameraSq() {
            return TranslucentSubmit.computeDistanceToCameraSq(pose.pose(), sortPos.getX() + 0.5f,
                    sortPos.getY() + 0.5f, sortPos.getZ() + 0.5f);
        }

        @Override
        public FeatureRendererType<Submit> featureType() {
            return TYPE;
        }
    }
}
