package guideme.scene;

import static guideme.scene.GuidebookLevelRenderer.getBlockRenderType;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import org.jetbrains.annotations.Nullable;

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
            var shared = submit.sharedTessellation;
            if (shared != null && shared.replay(this, submit.translucent)) {
                continue; // The other submit of this block already tessellated it
            }

            var blockState = submit.blockState;
            var model = context.blockStateModelSet().get(blockState);
            var forceOpaque = ModelBlockRenderer.forceOpaque(options.cutoutLeaves, blockState);

            poseStack.setIdentity();
            poseStack.last().set(submit.pose);
            BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
                var layer = forceOpaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                if (layer.translucent() == submit.translucent) {
                    putBakedQuad(getVertexBuilder(getBlockRenderType(layer)), x, y, z, quad, instance);
                } else if (shared != null) {
                    putBakedQuad(shared.recorder(layer), x, y, z, quad, instance);
                }
            };
            blockRenderer.tesselateBlock(quadOutput, 0, 0, 0, submit.level, submit.pos, blockState, model,
                    blockState.getSeed(submit.pos));
            if (shared != null) {
                shared.tessellated = true;
            }
        }
    }

    private void putBakedQuad(VertexConsumer consumer, float x, float y, float z, BakedQuad quad,
            QuadInstance instance) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        consumer.putBakedQuad(poseStack.last(), quad, instance);
        poseStack.popPose();
    }

    /**
     * Lets the non-translucent and the translucent submit of the same block share one tessellation of its model. The
     * submit that is built first tessellates the model and records the quads of the other submit, which then replays
     * them instead of tessellating the model again.
     */
    public static final class SharedTessellation {
        private final Map<ChunkSectionLayer, RecordingVertexConsumer> recordedLayers = new EnumMap<>(
                ChunkSectionLayer.class);
        private boolean tessellated;

        private RecordingVertexConsumer recorder(ChunkSectionLayer layer) {
            return recordedLayers.computeIfAbsent(layer, ignored -> new RecordingVertexConsumer());
        }

        /**
         * @return False if the model hasn't been tessellated yet.
         */
        private boolean replay(BlockModelFeatureRenderer renderer, boolean translucent) {
            if (!tessellated) {
                return false;
            }
            for (var entry : recordedLayers.entrySet()) {
                var layer = entry.getKey();
                if (layer.translucent() == translucent) {
                    entry.getValue().replay(renderer.getVertexBuilder(getBlockRenderType(layer)));
                }
            }
            return true;
        }
    }

    /**
     * @param pose               Transforms from the origin of the block at {@code pos}.
     * @param translucent        Whether to render only the translucent quads of the model, or only the non-translucent
     *                           ones. Translucent quads have to be submitted separately, since they're rendered in a
     *                           later phase.
     * @param sharedTessellation If the block is submitted both for its translucent and non-translucent quads, the same
     *                           instance passed to both submits avoids tessellating the model twice.
     */
    public record Submit(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack.Pose pose,
            BlockState blockState,
            boolean translucent,
            @Nullable SharedTessellation sharedTessellation) implements TranslucentSubmit {
        public Submit(BlockAndTintGetter level, BlockPos pos, PoseStack.Pose pose, BlockState blockState,
                boolean translucent) {
            this(level, pos, pose, blockState, translucent, null);
        }

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
