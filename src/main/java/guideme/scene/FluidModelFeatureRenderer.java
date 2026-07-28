package guideme.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.List;

import static guideme.scene.GuidebookLevelRenderer.getEntityRenderType;

public class FluidModelFeatureRenderer extends RenderTypeFeatureRenderer<FluidModelFeatureRenderer.Submit> {
    public static final FeatureRendererType<FluidModelFeatureRenderer.Submit> TYPE = FeatureRendererType.create("guideme:FluidModel");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
        var modelManager = Minecraft.getInstance().getModelManager();
        var fluidModelSet = modelManager.getFluidStateModelSet();
        var fluidRenderer = new FluidRenderer(fluidModelSet);

        for (var submit : submits) {
            var fluidState = submit.fluidState;
            var sectionPos = SectionPos.of(submit.pos);
            FluidRenderer.Output fluidOutput = layer -> {
                var baseBuffer = getVertexBuilder(getEntityRenderType(layer));
                return new LiquidVertexConsumer(baseBuffer, sectionPos);
            };

            var customRenderer = fluidModelSet.get(fluidState).customRenderer();
            if (customRenderer == null || !customRenderer.renderFluid(fluidRenderer, fluidState, submit.level, submit.pos, fluidOutput, submit.blockState)) {
                fluidRenderer.tesselate(submit.level, submit.pos, fluidOutput, submit.blockState, fluidState);
            }
        }
    }

    public record Submit(
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack.Pose pose,
            BlockState blockState,
            FluidState fluidState
    ) implements SubmitNode {
        @Override
        public FeatureRendererType<Submit> featureType() {
            return FluidModelFeatureRenderer.TYPE;
        }
    }
}
