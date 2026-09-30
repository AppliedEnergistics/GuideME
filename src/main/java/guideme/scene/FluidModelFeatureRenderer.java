package guideme.scene;

import static guideme.scene.GuidebookLevelRenderer.getEntityRenderType;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Renders the fluid of a single block in a guidebook level using the vanilla {@link FluidRenderer}, which is otherwise
 * only used for chunk section meshing.
 */
public class FluidModelFeatureRenderer extends RenderTypeFeatureRenderer<FluidModelFeatureRenderer.Submit> {
    public static final FeatureRendererType<FluidModelFeatureRenderer.Submit> TYPE = FeatureRendererType
            .create("guideme:FluidModel");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
        var fluidModelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        var fluidRenderer = new FluidRenderer(fluidModelSet);

        for (var submit : submits) {
            var fluidState = submit.fluidState;
            FluidRenderer.Output fluidOutput = layer -> new LiquidVertexConsumer(
                    getVertexBuilder(getEntityRenderType(layer)), submit.pose);

            var customRenderer = fluidModelSet.get(fluidState).customRenderer();
            if (customRenderer == null || !customRenderer.renderFluid(fluidRenderer, fluidState, submit.level,
                    submit.pos, fluidOutput, submit.blockState)) {
                fluidRenderer.tesselate(submit.level, submit.pos, fluidOutput, submit.blockState, fluidState);
            }
        }
    }

    /**
     * @param pose Transforms from the origin of the chunk section containing {@code pos}, since that is what
     *             {@link FluidRenderer} emits vertices relative to.
     */
    public record Submit(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack.Pose pose,
            BlockState blockState,
            FluidState fluidState) implements TranslucentSubmit {
        @Override
        public float distanceToCameraSq() {
            // Sort by the center of the block
            return TranslucentSubmit.computeDistanceToCameraSq(pose.pose(),
                    SectionPos.sectionRelative(pos.getX()) + 0.5f,
                    SectionPos.sectionRelative(pos.getY()) + 0.5f,
                    SectionPos.sectionRelative(pos.getZ()) + 0.5f);
        }

        @Override
        public FeatureRendererType<Submit> featureType() {
            return FluidModelFeatureRenderer.TYPE;
        }
    }
}
