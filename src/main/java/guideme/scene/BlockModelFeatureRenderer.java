package guideme.scene;

import static guideme.scene.GuidebookLevelRenderer.getBlockRenderType;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the block model of a single block in a guidebook level using the vanilla {@link ModelBlockRenderer}, the same
 * way it would be tessellated for a chunk section. This bakes ambient occlusion and directional shading into the vertex
 * colors and uses the block render pipelines, unlike the entity / item sheets used by
 * {@link net.minecraft.client.renderer.SubmitNodeCollector#submitMultiLayerBlockModel}.
 */
public class BlockModelFeatureRenderer extends RenderTypeFeatureRenderer<BlockModelFeatureRenderer.Submit> {
    public static final FeatureRendererType<BlockModelFeatureRenderer.Submit> TYPE = FeatureRendererType
            .create("guideme:BlockModel");

    private final PoseStack poseStack = new PoseStack();

    @Override
    protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
        var options = context.options();
        var blockRenderer = new ModelBlockRenderer(options.ambientOcclusion, true, context.blockColors());

        // Draws are ordered by first use of their render type, and geometry of the same render type is merged into
        // one draw. Models often have overlay quads (i.e. emissive parts) that are coplanar with the base quads and
        // only show up if they're drawn after them. Start the draws in the order Minecraft draws its chunk layers.
        // Draws that remain empty are skipped.
        getVertexBuilder(getBlockRenderType(ChunkSectionLayer.SOLID));
        getVertexBuilder(getBlockRenderType(ChunkSectionLayer.CUTOUT));

        for (var submit : submits) {
            var blockState = submit.blockState;
            var model = context.blockStateModelSet().get(blockState);
            var forceOpaque = ModelBlockRenderer.forceOpaque(options.cutoutLeaves, blockState);

            poseStack.setIdentity();
            poseStack.last().set(submit.pose);
            BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
                var layer = forceOpaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                if (layer.translucent() == submit.translucent) {
                    putBakedQuad(x, y, z, quad, instance, layer);
                }
            };
            blockRenderer.tesselateBlock(quadOutput, 0, 0, 0, submit.level, submit.pos, blockState, model,
                    blockState.getSeed(submit.pos));
        }
    }

    private void putBakedQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance,
            ChunkSectionLayer layer) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        getVertexBuilder(getBlockRenderType(layer)).putBakedQuad(poseStack.last(), quad, instance);
        poseStack.popPose();
    }

    /**
     * @param pose        Transforms from the origin of the block at {@code pos}.
     * @param translucent Whether to render only the translucent quads of the model, or only the non-translucent ones.
     *                    Translucent quads have to be submitted separately, since they're rendered in a later phase.
     */
    public record Submit(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack.Pose pose,
            BlockState blockState,
            boolean translucent) implements TranslucentSubmit {
        @Override
        public float distanceToCameraSq() {
            return TranslucentSubmit.computeDistanceToCameraSq(pose.pose(), 0.5f, 0.5f, 0.5f);
        }

        @Override
        public FeatureRendererType<Submit> featureType() {
            return BlockModelFeatureRenderer.TYPE;
        }
    }
}
