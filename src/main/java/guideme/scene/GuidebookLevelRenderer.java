package guideme.scene;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import guideme.color.LightDarkMode;
import guideme.internal.scene.FakeRenderEnvironment;
import guideme.scene.annotation.InWorldAnnotation;
import guideme.scene.annotation.InWorldAnnotationRenderer;
import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GuidebookLevelRenderer {

    private static GuidebookLevelRenderer instance;

    private final ProjectionMatrixBuffer projMatBuffer = new ProjectionMatrixBuffer(
            "GuideME level renderer proj mat UBO");

    private final SubmitNodeStorage submitNodeStorage = new SubmitNodeStorage();

    public static GuidebookLevelRenderer getInstance() {
        RenderSystem.assertOnRenderThread();
        if (instance == null) {
            instance = new GuidebookLevelRenderer();
        }
        return instance;
    }

    public void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            SubmitNodeStorage nodes,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode) {

        level.onRenderFrame();

        var minecraft = Minecraft.getInstance();
        var gameRenderer = minecraft.gameRenderer;
        var deltaTracker = new DeltaTracker() {
            @Override
            public float getGameTimeDeltaTicks() {
                throw new UnsupportedOperationException();
            }

            @Override
            public float getGameTimeDeltaPartialTick(boolean runsNormally) {
                return level.getPartialTick();
            }

            @Override
            public float getRealtimeDeltaTicks() {
                throw new UnsupportedOperationException();
            }
        };

        var globalSettingsUniform = gameRenderer.globalSettingsUniform;
        globalSettingsUniform
                .update(
                        cameraSettings.getViewportSize().width(),
                        cameraSettings.getViewportSize().height(),
                        minecraft.options.glintStrength().get(),
                        level.getGameTime(),
                        deltaTracker,
                        minecraft.options.getMenuBackgroundBlurriness(),
                        Vec3.ZERO,
                        minecraft.options.textureFiltering().get() == TextureFilteringMethod.RGSS);

        var lightEngine = level.getLightEngine();
        while (lightEngine.hasLightWork()) {
            lightEngine.runLightUpdates();
        }

        var projectionMatrix = cameraSettings.getProjectionMatrix();
        var viewMatrix = cameraSettings.getViewMatrix();

        // Essentially disable level fog
        RenderSystem.setShaderFog(gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(viewMatrix);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projMatBuffer.getBuffer(projectionMatrix), ProjectionType.ORTHOGRAPHIC);

        var lightDirection = new Vector4f(15 / 90f, .35f, 1, 0);
        var lightTransform = new Matrix4f(viewMatrix);
        lightTransform.invert();
        lightTransform.transform(lightDirection);

        gameRenderer.lighting().updateLevel(CardinalLighting.Type.DEFAULT);
        gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);

        var previousUseUiLightmap = gameRenderer.useUiLightmap;
        gameRenderer.useUiLightmap = false;
        var renderState = new LightmapRenderState();
        renderState.needsUpdate = true;
        gameRenderer.lightmap.render(renderState);
        try {
            renderContent(level, nodes, new PoseStack());

            InWorldAnnotationRenderer.render(nodes, annotations, lightDarkMode);

            gameRenderer.featureRenderDispatcher().renderAllFeatures(submitNodeStorage);
        } finally {
            gameRenderer.useUiLightmap = previousUseUiLightmap;
            gameRenderer.gameRenderState().lightmapRenderState.needsUpdate = true;
            gameRenderer.lightmap.render(gameRenderer.gameRenderState().lightmapRenderState);
        }

        modelViewStack.popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /**
     * Render without any setup.
     */
    public void renderContent(GuidebookLevel level, SubmitNodeStorage nodes,
            PoseStack poseStack) {
        try (var fake = FakeRenderEnvironment.create(level)) {
            renderBlocks(level, nodes, false, poseStack);
            renderBlockEntities(level, featureRenderDispatcher, level.getPartialTick(), poseStack);
            renderEntities(level, level.getPartialTick(), poseStack, featureRenderDispatcher);
        }
    }

    private static RenderType getEntityRenderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID, CUTOUT -> Sheets.cutoutBlockSheet();
            case TRANSLUCENT -> Sheets.translucentBlockSheet();
        };
    }

    private void renderBlocks(GuidebookLevel level, SubmitNodeStorage nodes, PoseStack poseStack) {
        var minecraft = Minecraft.getInstance();
        boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
        var blockRenderer = new ModelBlockRenderer(ambientOcclusion, false, minecraft.getBlockColors());
        var modelManager = minecraft.getModelManager();
        var fluidModelSet = modelManager.getFluidStateModelSet();
        var fluidRenderer = new FluidRenderer(fluidModelSet);

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            var layer = quad.materialInfo().layer();
            if (layer.translucent() == translucent) {
                var builder = buffers.getVertexBuilder(getEntityRenderType(layer));
                builder.putBakedQuad(poseStack.last(), quad, instance);
            }
        };

        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            var fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                var fluidModel = minecraft.getModelManager().getFluidStateModelSet().get(fluidState);
                fluidModel.customRenderer()
                var sectionPos = SectionPos.of(pos);
                FluidRenderer.Output fluidOutput = layer -> {
                    if (layer.translucent() == translucent) {
                        var baseBuffer = buffers.getBuffer(getEntityRenderType(layer));
                        return new LiquidVertexConsumer(baseBuffer, sectionPos);
                    } else {
                        return NoopVertexConsumer.INSTANCE;
                    }
                };

                var customRenderer = fluidModelSet.get(fluidState).customRenderer();
                if (customRenderer == null || !customRenderer.renderFluid(fluidRenderer, fluidState, level, pos, fluidOutput, blockState)) {
                    fluidRenderer.tesselate(level, pos, fluidOutput, blockState, fluidState);
                }
            }

            if (blockState.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }

            var model = modelManager.getBlockStateModelSet().get(blockState);
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            blockRenderer.tesselateBlock(quadOutput, 0, 0, 0, level, pos, blockState, model, blockState.getSeed(pos));
            poseStack.popPose();
        }
    }

    private void renderBlockEntities(GuidebookLevel level, FeatureRenderDispatcher dispatcher, float partialTick,
            PoseStack poseStack) {
        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            if (blockState.hasBlockEntity()) {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.handleBlockEntity(poseStack, blockEntity, partialTick, dispatcher.getSubmitNodeStorage());
                }
            }
        }

        dispatcher.renderAllFeatures();
    }

    private <E extends BlockEntity> void handleBlockEntity(PoseStack stack,
            E blockEntity,
            float partialTicks,
            SubmitNodeCollector nodeCollector) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(blockEntity);
        if (renderer != null && renderer.shouldRender(blockEntity, blockEntity.getBlockPos().getCenter())) {
            var pos = blockEntity.getBlockPos();
            stack.pushPose();
            stack.translate(pos.getX(), pos.getY(), pos.getZ());

            renderBlockEntity(stack, blockEntity, partialTicks, renderer, nodeCollector);
            stack.popPose();
        }
    }

    private static <E extends BlockEntity, S extends BlockEntityRenderState> void renderBlockEntity(PoseStack stack,
            E blockEntity,
            float partialTicks,
            BlockEntityRenderer<E, S> renderer,
            SubmitNodeCollector nodeCollector) {
        var state = renderer.createRenderState();
        renderer.extractRenderState(blockEntity, state, partialTicks, Vec3.ZERO, null);
        renderer.submit(state, stack, nodeCollector, new CameraRenderState());
    }

    private void renderEntities(GuidebookLevel level,
            float partialTick,
            PoseStack poseStack,
            FeatureRenderDispatcher featureRenderDispatcher) {
        for (var entity : level.getEntitiesForRendering()) {
            handleEntity(poseStack, featureRenderDispatcher.getSubmitNodeStorage(), entity, partialTick);
        }

        featureRenderDispatcher.renderAllFeatures();
    }

    private <E extends Entity> void handleEntity(PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            E entity,
            float partialTicks) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(entity);
        if (renderer == null) {
            return;
        }

        renderEntity(poseStack, submitNodeCollector, entity, partialTicks, renderer);
    }

    private static <E extends Entity, S extends EntityRenderState> void renderEntity(PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            E entity,
            float partialTicks,
            EntityRenderer<? super E, S> renderer) {
        var probePos = BlockPos.containing(entity.getLightProbePosition(partialTicks));

        var pos = entity.position();
        var state = renderer.createRenderState(entity, partialTicks);
        var offset = renderer.getRenderOffset(state);
        poseStack.pushPose();
        poseStack.translate(pos.x + offset.x(), pos.y + offset.y(), pos.z + offset.z());
        renderer.submit(state, poseStack, submitNodeCollector, new CameraRenderState());
        poseStack.popPose();
    }
}
