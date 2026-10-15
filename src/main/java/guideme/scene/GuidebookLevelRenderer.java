package guideme.scene;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import guideme.color.LightDarkMode;
import guideme.internal.GuideMEClient;
import guideme.scene.annotation.InWorldAnnotation;
import guideme.scene.annotation.InWorldAnnotationRenderer;
import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GuidebookLevelRenderer {

    private static GuidebookLevelRenderer instance;

    /**
     * Cached scene geometry is freed when the scene hasn't been rendered for this long.
     */
    private static final long MESH_CACHE_EXPIRY_MS = 30_000;

    private final GuidebookLightmap lightmap = new GuidebookLightmap();

    private final Map<GuidebookLevel, SceneMeshCache> meshCaches = new IdentityHashMap<>();

    public static GuidebookLevelRenderer getInstance() {
        RenderSystem.assertOnRenderThread();
        if (instance == null) {
            instance = new GuidebookLevelRenderer();
        }
        return instance;
    }

    public void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode) {
        render(level, cameraSettings, annotations, lightDarkMode, GuideMEClient.instance().isCacheSceneMeshes());
    }

    void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode,
            boolean cacheMeshes) {

        lightmap.update(level);

        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        level.onRenderFrame();

        RenderSystem.setShaderGameTime(level.getGameTime(), level.getPartialTick());

        SceneMeshCache meshCache = null;
        if (cacheMeshes) {
            meshCache = meshCaches.computeIfAbsent(level, ignored -> new SceneMeshCache());
            meshCache.lastUsed = Util.getMillis();
        }

        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        render(level, cameraSettings, buffers, annotations, lightDarkMode, meshCache);
        buffers.endBatch();

        // Do not clear depth here anymore, since we clear it after rendering the document
    }

    public void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            MultiBufferSource.BufferSource buffers,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode) {
        render(level, cameraSettings, buffers, annotations, lightDarkMode, null);
    }

    private void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            MultiBufferSource.BufferSource buffers,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode,
            @Nullable SceneMeshCache meshCache) {
        lightmap.update(level);

        var lightEngine = level.getLightEngine();
        while (lightEngine.hasLightWork()) {
            lightEngine.runLightUpdates();
        }

        var projectionMatrix = cameraSettings.getProjectionMatrix();
        var viewMatrix = cameraSettings.getViewMatrix();

        // Essentially disable level fog
        RenderSystem.setShaderFogColor(1, 1, 1, 0);
        RenderSystem.setShaderFogStart(0);
        RenderSystem.setShaderFogEnd(1000);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(viewMatrix);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

        var lightDirection = new Vector4f(15 / 90f, .35f, 1, 0);
        var lightTransform = new Matrix4f(viewMatrix);
        lightTransform.invert();
        lightTransform.transform(lightDirection);

        Lighting.setupLevel();

        renderContent(level, buffers, meshCache);

        InWorldAnnotationRenderer.render(buffers, annotations, lightDarkMode);

        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();

        Lighting.setupFor3DItems(); // Reset to GUI lighting
    }

    /**
     * Render without any setup.
     */
    public void renderContent(GuidebookLevel level, MultiBufferSource.BufferSource buffers) {
        renderContent(level, buffers, null);
    }

    /**
     * @param meshCache If given, block and fluid geometry is drawn from this cache instead of being tessellated.
     */
    private void renderContent(GuidebookLevel level, MultiBufferSource.BufferSource buffers,
            @Nullable SceneMeshCache meshCache) {
        RenderSystem.runAsFancy(() -> {
            List<DeferredBlock> translucentBlocks;
            if (meshCache != null) {
                meshCache.prepare(level);
                meshCache.markSpritesActive();
                translucentBlocks = List.of();
            } else {
                translucentBlocks = renderBlocks(level, buffers);
            }
            renderBlockEntities(level, buffers, level.getPartialTick());
            renderEntities(level, buffers, level.getPartialTick());

            // The order comes from LevelRenderer#renderLevel
            buffers.endBatch(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
            buffers.endBatch(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            buffers.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            buffers.endBatch(RenderType.entitySmoothCutout(TextureAtlas.LOCATION_BLOCKS));

            // These would normally be pre-baked, but they are not for us
            for (var layer : RenderType.chunkBufferLayers()) {
                if (layer != RenderType.translucent()) {
                    if (meshCache != null) {
                        meshCache.draw(layer);
                    }
                    buffers.endBatch(layer);
                }
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

            if (meshCache != null) {
                meshCache.draw(RenderType.translucent());
            } else {
                renderTranslucentBlocks(level, buffers, translucentBlocks);
            }
            buffers.endBatch(RenderType.translucent());
        });
    }

    /**
     * Frees all cached scene geometry. Scenes that are still shown rebuild it the next time they are rendered.
     */
    @ApiStatus.Internal
    public static void freeMeshCaches() {
        if (instance == null) {
            return;
        }

        for (var meshCache : instance.meshCaches.values()) {
            meshCache.close();
        }
        instance.meshCaches.clear();
    }

    /**
     * Frees cached scene geometry that hasn't been used recently. The guide screen frees all cached geometry when it
     * changes pages or closes, so this is for scenes rendered elsewhere.
     */
    @ApiStatus.Internal
    public static void freeUnusedMeshCaches() {
        if (instance == null) {
            return;
        }

        var now = Util.getMillis();
        var it = instance.meshCaches.values().iterator();
        while (it.hasNext()) {
            var meshCache = it.next();
            if (now - meshCache.lastUsed > MESH_CACHE_EXPIRY_MS) {
                meshCache.close();
                it.remove();
            }
        }
    }

    /**
     * Renders all non-translucent block and fluid geometry
     * 
     * @return the blocks that still have translucent geometry to render, so that the translucent pass can reuse the
     *         model data computed here.
     */
    static List<DeferredBlock> renderBlocks(GuidebookLevel level, MultiBufferSource buffers) {
        var randomSource = level.random;
        var blockRenderDispatcher = Minecraft.getInstance().getBlockRenderer();
        var poseStack = new PoseStack();
        var translucentBlocks = new ArrayList<DeferredBlock>();

        // Normally only enabled while compiling chunk sections, this caches light values for ambient occlusion
        ModelBlockRenderer.enableCaching();
        try {
            level.getFilledBlocks().forEach(pos -> {
                var blockState = level.getBlockState(pos);
                var fluidState = blockState.getFluidState();
                boolean translucentFluid = false;
                if (!fluidState.isEmpty()) {
                    var renderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                    if (renderType != RenderType.translucent()) {
                        renderFluid(level, pos, blockState, fluidState, buffers.getBuffer(renderType));
                    } else {
                        translucentFluid = true;
                    }
                }

                ModelData modelData = ModelData.EMPTY;
                boolean translucentBlock = false;
                if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
                    var model = blockRenderDispatcher.getBlockModel(blockState);
                    modelData = getModelData(level, pos, blockState, model);
                    var renderTypes = model.getRenderTypes(blockState, randomSource, modelData);

                    for (var renderType : renderTypes) {
                        if (renderType != RenderType.translucent()) {
                            renderBlock(level, pos, blockState, modelData, renderType, poseStack,
                                    buffers.getBuffer(renderType));
                        } else {
                            translucentBlock = true;
                        }
                    }
                }

                if (translucentFluid || translucentBlock) {
                    translucentBlocks.add(new DeferredBlock(pos.immutable(), blockState, translucentFluid,
                            translucentBlock, modelData));
                }
            });
        } finally {
            ModelBlockRenderer.clearCache();
        }

        return translucentBlocks;
    }

    static void renderTranslucentBlocks(GuidebookLevel level, MultiBufferSource buffers,
            List<DeferredBlock> translucentBlocks) {
        var poseStack = new PoseStack();
        var renderType = RenderType.translucent();

        ModelBlockRenderer.enableCaching();
        try {
            for (var block : translucentBlocks) {
                if (block.fluid()) {
                    renderFluid(level, block.pos(), block.state(), block.state().getFluidState(),
                            buffers.getBuffer(renderType));
                }
                if (block.block()) {
                    renderBlock(level, block.pos(), block.state(), block.modelData(), renderType, poseStack,
                            buffers.getBuffer(renderType));
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }

    private static ModelData getModelData(GuidebookLevel level, BlockPos pos, BlockState blockState,
            BakedModel model) {
        var be = level.getBlockEntity(pos);
        ModelData modelData = (be != null) ? be.getModelData() : ModelData.EMPTY;
        return model.getModelData(level, pos, blockState, modelData);
    }

    private static void renderFluid(GuidebookLevel level, BlockPos pos, BlockState blockState,
            FluidState fluidState, VertexConsumer consumer) {
        var sectionPos = SectionPos.of(pos);
        var liquidVertexConsumer = new LiquidVertexConsumer(consumer, sectionPos);
        Minecraft.getInstance().getBlockRenderer().renderLiquid(pos, level, liquidVertexConsumer, blockState,
                fluidState);

        markFluidSpritesActive(fluidState);
    }

    private static void renderBlock(GuidebookLevel level, BlockPos pos, BlockState blockState, ModelData modelData,
            RenderType renderType, PoseStack poseStack, VertexConsumer consumer) {
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Minecraft.getInstance().getBlockRenderer().renderBatched(blockState, pos, level, poseStack, consumer, true,
                level.random, modelData, renderType);
        poseStack.popPose();
    }

    /**
     * A block whose translucent geometry is rendered in a later pass than its other geometry.
     *
     * @param fluid Whether the block has a translucent fluid.
     * @param block Whether the block model has translucent geometry.
     */
    record DeferredBlock(BlockPos pos, BlockState state, boolean fluid, boolean block, ModelData modelData) {
    }

    private void renderBlockEntities(GuidebookLevel level, MultiBufferSource buffers, float partialTick) {
        var poseStack = new PoseStack();

        level.getFilledBlocks().forEach(pos -> {
            var blockState = level.getBlockState(pos);
            if (blockState.hasBlockEntity()) {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.handleBlockEntity(poseStack, blockEntity, buffers, partialTick);
                }
            }
        });
    }

    static void markFluidSpritesActive(FluidState fluidState) {
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
            renderer.render(blockEntity, partialTicks, stack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
    }

    private void renderEntities(GuidebookLevel level, MultiBufferSource.BufferSource buffers, float partialTick) {
        var poseStack = new PoseStack();

        for (var entity : level.getEntitiesForRendering()) {
            handleEntity(level, poseStack, entity, buffers, partialTick);
        }
    }

    private <E extends Entity> void handleEntity(GuidebookLevel level,
            PoseStack poseStack,
            E entity,
            MultiBufferSource buffers,
            float partialTicks) {
        // Entity rendering does not work correctly if there's no level/player
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        var dispatcher = minecraft.getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(entity);
        if (renderer == null) {
            return;
        }

        var probePos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        int packedLight = LevelRenderer.getLightColor(level, probePos);
        var yaw = entity.getYRot();

        var pos = entity.position();
        var offset = renderer.getRenderOffset(entity, partialTicks);
        poseStack.pushPose();
        poseStack.translate(pos.x + offset.x(), pos.y + offset.y(), pos.z + offset.z());
        renderer.render(
                entity,
                yaw,
                partialTicks,
                poseStack,
                buffers,
                packedLight);
        poseStack.popPose();
    }
}
