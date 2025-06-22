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
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GuidebookLevelRenderer {

    private static GuidebookLevelRenderer instance;

    private final GuidebookLightmap lightmap = new GuidebookLightmap();

    private final PerspectiveProjectionMatrixBuffer projMatBuffer = new PerspectiveProjectionMatrixBuffer(
            "GuideME level renderer proj mat UBO");

    public static GuidebookLevelRenderer getInstance() {
        RenderSystem.assertOnRenderThread();
        if (instance == null) {
            instance = new GuidebookLevelRenderer();
        }
        return instance;
    }

    public void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            MultiBufferSource.BufferSource buffers,
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
        var globalSettingsUniform = gameRenderer.getGlobalSettingsUniform();
        globalSettingsUniform
                .update(
                        cameraSettings.getViewportSize().width(),
                        cameraSettings.getViewportSize().height(),
                        minecraft.options.glintStrength().get(),
                        level.getGameTime(),
                        deltaTracker,
                        minecraft.options.getMenuBackgroundBlurriness());

        lightmap.update(level);

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

        gameRenderer.getLighting().updateLevel(false);
        gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);

        var previousLightmap = gameRenderer.lightTexture().textureView;
        gameRenderer.lightTexture().textureView = lightmap.getTextureView();
        try {
            renderContent(level, buffers, new PoseStack());

            InWorldAnnotationRenderer.render(buffers, annotations, lightDarkMode);

            buffers.endBatch();
        } finally {
            gameRenderer.lightTexture().textureView = previousLightmap;
        }

        modelViewStack.popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /**
     * Render without any setup.
     */
    public void renderContent(GuidebookLevel level, MultiBufferSource.BufferSource buffers, PoseStack poseStack) {
        try (var fake = FakeRenderEnvironment.create(level)) {
            renderBlocks(level, buffers, false, poseStack);
            renderBlockEntities(level, buffers, level.getPartialTick(), poseStack);
            renderEntities(level, buffers, level.getPartialTick(), poseStack);

            // The order comes from LevelRenderer#renderLevel
            buffers.endBatch(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
            buffers.endBatch(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            buffers.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            buffers.endBatch(RenderType.entitySmoothCutout(TextureAtlas.LOCATION_BLOCKS));

            // These would normally be pre-baked, but they are not for us
            for (var layer : ChunkSectionLayerGroup.OPAQUE.layers()) {
                buffers.endBatch(RenderTypeHelper.getEntityRenderType(layer));
            }

            buffers.endBatch(RenderType.solid());
            buffers.endBatch(RenderType.endPortal());
            buffers.endBatch(RenderType.endGateway());
            buffers.endBatch(Sheets.solidBlockSheet());
            buffers.endBatch(Sheets.cutoutBlockSheet());
            buffers.endBatch(Sheets.bedSheet());
            buffers.endBatch(Sheets.shulkerBoxSheet());
            buffers.endBatch(Sheets.signSheet());
            buffers.endBatch(Sheets.hangingSignSheet());
            buffers.endBatch(Sheets.chestSheet());
            buffers.endLastBatch();

            renderBlocks(level, buffers, true, poseStack);
            for (var group : ChunkSectionLayerGroup.values()) {
                if (group == ChunkSectionLayerGroup.OPAQUE) {
                    continue;
                }
                for (var layer : group.layers()) {
                    buffers.endBatch(RenderTypeHelper.getEntityRenderType(layer));
                }
            }
        }
    }

    private void renderBlocks(GuidebookLevel level, MultiBufferSource buffers, boolean translucent,
            PoseStack poseStack) {
        var blockRenderDispatcher = Minecraft.getInstance().getBlockRenderer();

        var randomSource = new SingleThreadedRandomSource(0L);
        var modelParts = new ArrayList<BlockModelPart>();

        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            var fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                var layer = ItemBlockRenderTypes.getRenderLayer(fluidState);
                if (layer != ChunkSectionLayer.TRANSLUCENT || translucent) {
                    var bufferBuilder = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer));

                    var sectionPos = SectionPos.of(pos);
                    var liquidVertexConsumer = new LiquidVertexConsumer(bufferBuilder, sectionPos);
                    blockRenderDispatcher.renderLiquid(pos, level, liquidVertexConsumer, blockState, fluidState);

                    markFluidSpritesActive(fluidState);
                }
            }

            if (blockState.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }

            var model = blockRenderDispatcher.getBlockModel(blockState);

            modelParts.clear();
            randomSource.setSeed(blockState.getSeed(pos));
            model.collectParts(level, pos, blockState, randomSource, modelParts);
            if (!translucent) {
                modelParts.removeIf(part -> {
                    var layer = part.getRenderType(blockState);
                    return layer == ChunkSectionLayer.TRANSLUCENT || layer == ChunkSectionLayer.TRIPWIRE;
                });
            }

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            blockRenderDispatcher.renderBatched(blockState, pos, level, poseStack, layer -> {
                return buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer));
            }, true,
                    modelParts);
            poseStack.popPose();
        }
    }

    private void renderBlockEntities(GuidebookLevel level, MultiBufferSource buffers, float partialTick,
            PoseStack poseStack) {
        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            if (blockState.hasBlockEntity()) {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.handleBlockEntity(poseStack, blockEntity, buffers, partialTick);
                }
            }
        }
    }

    private static void markFluidSpritesActive(FluidState fluidState) {
        // For Sodium compatibility, ensure the sprites actually animate even if no block is on-screen
        // that would cause them to, otherwise.
        var props = IClientFluidTypeExtensions.of(fluidState);
        var sprite1 = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(props.getStillTexture());
        SodiumCompat.markSpriteActive(sprite1);
        var sprite2 = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(props.getFlowingTexture());
        SodiumCompat.markSpriteActive(sprite2);
    }

    private <E extends BlockEntity> void handleBlockEntity(PoseStack stack,
            E blockEntity,
            MultiBufferSource buffers,
            float partialTicks) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(blockEntity);
        if (renderer != null && renderer.shouldRender(blockEntity, blockEntity.getBlockPos().getCenter())) {
            var pos = blockEntity.getBlockPos();
            stack.pushPose();
            stack.translate(pos.getX(), pos.getY(), pos.getZ());

            int packedLight = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos());
            renderer.render(blockEntity, partialTicks, stack, buffers, packedLight, OverlayTexture.NO_OVERLAY,
                    Vec3.ZERO);
            stack.popPose();
        }
    }

    private void renderEntities(GuidebookLevel level, MultiBufferSource.BufferSource buffers, float partialTick,
            PoseStack poseStack) {
        for (var entity : level.getEntitiesForRendering()) {
            handleEntity(level, poseStack, entity, buffers, partialTick);
        }
    }

    private <E extends Entity> void handleEntity(GuidebookLevel level,
            PoseStack poseStack,
            E entity,
            MultiBufferSource buffers,
            float partialTicks) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(entity);
        if (renderer == null) {
            return;
        }

        renderEntity(level, poseStack, entity, buffers, partialTicks, renderer);
    }

    private static <E extends Entity, S extends EntityRenderState> void renderEntity(GuidebookLevel level,
            PoseStack poseStack,
            E entity,
            MultiBufferSource buffers,
            float partialTicks,
            EntityRenderer<? super E, S> renderer) {
        var probePos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        int packedLight = LevelRenderer.getLightColor(level, probePos);

        var pos = entity.position();
        var state = renderer.createRenderState(entity, partialTicks);
        var offset = renderer.getRenderOffset(state);
        poseStack.pushPose();
        poseStack.translate(pos.x + offset.x(), pos.y + offset.y(), pos.z + offset.z());
        renderer.render(state, poseStack, buffers, packedLight);
        poseStack.popPose();
    }
}
