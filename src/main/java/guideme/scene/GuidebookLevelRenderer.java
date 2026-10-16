package guideme.scene;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import guideme.color.LightDarkMode;
import guideme.internal.scene.FakeRenderEnvironment;
import guideme.internal.scene.SceneRenderTarget;
import guideme.internal.util.Platform;
import guideme.scene.annotation.InWorldAnnotation;
import guideme.scene.annotation.InWorldAnnotationRenderer;
import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.config.NeoForgeClientConfig;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public class GuidebookLevelRenderer {

    private static GuidebookLevelRenderer instance;

    /**
     * When a user scrolls away from a scene in a long page, its cached mesh will be kept for this long. If it comes
     * back into view, the cache can then be reused.
     */
    private static final long MESH_CACHE_EXPIRY_MS = 30_000;

    private final Map<GuidebookLevel, SceneMeshCache> meshCaches = new IdentityHashMap<>();

    private final ProjectionMatrixBuffer projMatBuffer = new ProjectionMatrixBuffer(
            "GuideME level renderer proj mat UBO");

    /**
     * Cached local player used for faking the render environment for vanilla and modded renderers. Cached since
     * initializing it involves also creating a fake level et al.
     */
    @Nullable
    private LocalPlayer fakePlayer;

    /**
     * The registries the cached fake player was built against. The fake player embeds them, so it has to be rebuilt
     * whenever the client switches to a world that has different one.
     */
    @Nullable
    private RegistryAccess fakePlayerRegistries;

    /**
     * Our own light directions for scenes. We do not use the game renderer's, since updating its level lighting would
     * clobber the lighting of the dimension the player is currently in (e.g. the nether).
     */
    @Nullable
    private Lighting lighting;

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
        var meshCache = meshCaches.computeIfAbsent(level, ignored -> new SceneMeshCache());
        meshCache.lastUsed = Util.getMillis();
        render(level, cameraSettings, annotations, lightDarkMode, meshCache);
    }

    /**
     * @param meshCache The cache to draw the block and fluid geometry from. It's rebuilt if it's out of date.
     */
    void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode,
            SceneMeshCache meshCache) {

        level.onRenderFrame();

        var minecraft = Minecraft.getInstance();
        var gameRenderer = minecraft.gameRenderer;
        var globalSettingsUniform = gameRenderer.globalSettingsUniform;
        globalSettingsUniform
                .update(
                        cameraSettings.getViewportSize().width(),
                        cameraSettings.getViewportSize().height(),
                        minecraft.options.glintStrength().get(),
                        level.getGameTime(),
                        level.getPartialTick(),
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

        // Scenes are rendered while the GUI is being drawn, where the bound lights are meant for GUI space
        // (ITEMS_3D, ENTITY_IN_UI, ...) and would light the scene from below. Bind world-space level lighting instead.
        var previousShaderLights = RenderSystem.getShaderLights();
        getLighting().setupFor(Lighting.Entry.LEVEL);

        // Use the UI lightmap (full brightness everywhere) instead of the lightmap of the level the player is in.
        // Re-rendering the level lightmap is not an option, since its ring buffer only supports one update per frame.
        var previousUseUiLightmap = gameRenderer.useUiLightmap;
        gameRenderer.useUiLightmap = true;
        try {
            var ns = new SubmitNodeStorage();
            renderContent(level, ns, new PoseStack(), meshCache);

            SceneRenderTarget.renderAllFeatures(gameRenderer.featureRenderDispatcher(), ns, () -> "GuideME scene");

            // Annotations depend on the depth buffer of the rendered scene, so they have to come afterward
            InWorldAnnotationRenderer.render(gameRenderer.featureRenderDispatcher(), annotations, lightDarkMode);
        } finally {
            gameRenderer.useUiLightmap = previousUseUiLightmap;
            if (previousShaderLights != null) {
                RenderSystem.setShaderLights(previousShaderLights);
            }
        }

        modelViewStack.popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /**
     * Render without any setup. Unlike {@link #render}, this tessellates the blocks and fluids of the level on every
     * call, since their geometry isn't kept.
     */
    public void renderContent(GuidebookLevel level, SubmitNodeCollector nodes,
            PoseStack poseStack) {
        renderContent(level, nodes, poseStack, new SceneMeshCache());
    }

    private void renderContent(GuidebookLevel level, SubmitNodeCollector nodes, PoseStack poseStack,
            SceneMeshCache meshCache) {
        try (var fake = FakeRenderEnvironment.create(getFakePlayer())) {
            prepareMeshCache(level, meshCache);
            for (var sprite : meshCache.getAnimatedSprites()) {
                SodiumCompat.markSpriteActive(sprite);
            }

            // The submits are rendered later, so we cannot hold on to the caller's pose
            submitMeshCache(meshCache, nodes, poseStack.last().copy());
            renderBlockEntities(level, level.getPartialTick(), poseStack, nodes);
            renderEntities(level, level.getPartialTick(), poseStack, nodes);
        }
    }

    /**
     * Rebuilds the cached block and fluid geometry if it is out of date.
     */
    private void prepareMeshCache(GuidebookLevel level, SceneMeshCache meshCache) {
        RenderSystem.assertOnRenderThread();

        var minecraft = Minecraft.getInstance();
        var options = minecraft.gameRenderer.gameRenderState().optionsRenderState;
        var modelManager = minecraft.getModelManager();
        var key = new SceneMeshCache.Key(
                level.getRevision(),
                options.ambientOcclusion,
                options.cutoutLeaves,
                NeoForgeClientConfig.INSTANCE.handleAmbientOcclusionPerPart.getAsBoolean(),
                modelManager.getBlockStateModelSet(),
                modelManager.getFluidStateModelSet());
        if (!meshCache.isUpToDate(key)) {
            meshCache.clear();
            renderBlocks(level, meshCache, options, key.blockModels(), key.fluidModels());
            meshCache.markBuilt(key);
        }
    }

    /**
     * Submits the cached geometry for rendering. Non-translucent geometry is submitted for the whole scene at once,
     * translucent geometry per block, so it is sorted together with the translucent geometry of block entities and
     * entities.
     *
     * @param pose Transforms from level coordinates, which the geometry was recorded in.
     */
    private static void submitMeshCache(SceneMeshCache meshCache, SubmitNodeCollector nodes, PoseStack.Pose pose) {
        var opaqueLayers = meshCache.getOpaqueLayers();
        if (!opaqueLayers.isEmpty()) {
            var layers = new ArrayList<SceneMeshFeatureRenderer.Layer>(opaqueLayers.size());
            for (var entry : opaqueLayers.entrySet()) {
                layers.add(new SceneMeshFeatureRenderer.Layer(entry.getKey(), entry.getValue()));
            }
            nodes.submitSpecial(RenderPhaseKeys.SOLID,
                    new SceneMeshFeatureRenderer.Submit(pose, BlockPos.ZERO, layers));
        }
        for (var translucentBlock : meshCache.getTranslucentBlocks()) {
            nodes.submitSpecial(RenderPhaseKeys.TRANSLUCENT_BLOCKS_AND_ITEMS, new SceneMeshFeatureRenderer.Submit(pose,
                    translucentBlock.pos(), List.of(new SceneMeshFeatureRenderer.Layer(ChunkSectionLayer.TRANSLUCENT,
                            translucentBlock.geometry()))));
        }
    }

    /**
     * Collects the geometry of one block: non-translucent geometry goes to the scene-wide layers, translucent geometry
     * is kept for the block.
     */
    private static final class BlockGeometry {
        private final SceneMeshCache meshCache;
        private BlockPos pos = BlockPos.ZERO;
        @Nullable
        private RecordingVertexConsumer translucent;

        BlockGeometry(SceneMeshCache meshCache) {
            this.meshCache = meshCache;
        }

        void start(BlockPos pos) {
            this.pos = pos;
            this.translucent = null;
        }

        RecordingVertexConsumer consumer(ChunkSectionLayer layer) {
            if (layer.translucent()) {
                if (translucent == null) {
                    translucent = new RecordingVertexConsumer();
                }
                return translucent;
            }
            return meshCache.getOpaqueLayer(layer);
        }

        void finish() {
            if (translucent != null && !translucent.isEmpty()) {
                meshCache.addTranslucentBlock(pos, translucent);
            }
        }
    }

    private LocalPlayer getFakePlayer() {
        var registries = Platform.getClientRegistryAccess();
        if (fakePlayer == null || fakePlayerRegistries != registries) {
            fakePlayer = FakeRenderEnvironment.createFakePlayer(registries);
            fakePlayerRegistries = registries;
        }
        return fakePlayer;
    }

    private Lighting getLighting() {
        if (lighting == null) {
            lighting = new Lighting();
            lighting.updateLevel(CardinalLighting.Type.DEFAULT);
        }
        return lighting;
    }

    /**
     * Drops the cached fake player so that leaving a world does not keep that world's registries alive.
     */
    @ApiStatus.Internal
    public void clearCache() {
        fakePlayer = null;
        fakePlayerRegistries = null;
        meshCaches.clear();
    }

    /**
     * Frees all cached scene geometry. Scenes that are still shown rebuild it the next time they are rendered.
     */
    @ApiStatus.Internal
    public void freeMeshCaches() {
        meshCaches.clear();
    }

    /**
     * Frees cached scene geometry that hasn't been used recently. The guide screen frees all cached geometry when it
     * changes pages or closes, so this is for scenes rendered elsewhere.
     */
    @ApiStatus.Internal
    public void freeUnusedMeshCaches() {
        var now = Util.getMillis();
        instance.meshCaches.values().removeIf(meshCache -> now - meshCache.lastUsed > MESH_CACHE_EXPIRY_MS);
    }

    /**
     * The render types Minecraft uses for moving blocks (i.e. pistons), which use the same pipelines as chunk sections.
     * We do not use the entity / item block sheets, since they apply directional lighting on top of the shading that is
     * already baked into the vertex colors by {@link ModelBlockRenderer}.
     */
    static RenderType getBlockRenderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    /**
     * Tessellates blocks with the vanilla {@link ModelBlockRenderer} and fluids with the vanilla {@link FluidRenderer},
     * the same way they would be tessellated for a chunk section.
     */
    private void renderBlocks(GuidebookLevel level, SceneMeshCache meshCache, OptionsRenderState options,
            BlockStateModelSet blockModels, FluidStateModelSet fluidModels) {
        var blockRenderer = new ModelBlockRenderer(options.ambientOcclusion, true,
                Minecraft.getInstance().getBlockColors());
        var fluidRenderer = new FluidRenderer(fluidModels);
        var poseStack = new PoseStack();
        var block = new BlockGeometry(meshCache);

        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next().immutable();
            var blockState = level.getBlockState(pos);
            block.start(pos);

            var fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                var fluidModel = fluidModels.get(fluidState);
                meshCache.addAnimatedMaterial(fluidModel.stillMaterial());
                meshCache.addAnimatedMaterial(fluidModel.flowingMaterial());
                meshCache.addAnimatedMaterial(fluidModel.overlayMaterial());

                // The fluid renderer emits vertices relative to the chunk section origin
                var sectionPos = SectionPos.of(pos);
                poseStack.pushPose();
                poseStack.translate(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ());
                var sectionPose = poseStack.last().copy();
                poseStack.popPose();
                FluidRenderer.Output fluidOutput = layer -> new LiquidVertexConsumer(block.consumer(layer),
                        sectionPose);

                var customRenderer = fluidModel.customRenderer();
                if (customRenderer == null || !customRenderer.renderFluid(fluidRenderer, fluidState, level, pos,
                        fluidOutput, blockState)) {
                    fluidRenderer.tesselate(level, pos, fluidOutput, blockState, fluidState);
                }
            }

            if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
                var model = blockModels.get(blockState);
                var forceOpaque = ModelBlockRenderer.forceOpaque(options.cutoutLeaves, blockState);
                // Translucent quads are only drawn for models with this flag
                var hasTranslucent = model.hasMaterialFlag(level, pos, blockState, BakedQuad.FLAG_TRANSLUCENT);

                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                blockRenderer.tesselateBlock((x, y, z, quad, instance) -> {
                    var layer = forceOpaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                    if (layer.translucent() && !hasTranslucent) {
                        return;
                    }
                    var sprite = quad.materialInfo().sprite();
                    if (sprite.isAnimated()) {
                        meshCache.addAnimatedSprite(sprite);
                    }
                    poseStack.pushPose();
                    poseStack.translate(x, y, z);
                    block.consumer(layer).putBakedQuad(poseStack.last(), quad, instance);
                    poseStack.popPose();
                }, 0, 0, 0, level, pos, blockState, model, blockState.getSeed(pos));
                poseStack.popPose();
            }

            block.finish();
        }
    }

    private void renderBlockEntities(GuidebookLevel level, float partialTick, PoseStack poseStack,
            SubmitNodeCollector nodes) {
        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            if (blockState.hasBlockEntity()) {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.handleBlockEntity(poseStack, blockEntity, partialTick, nodes);
                }
            }
        }
    }

    private <E extends BlockEntity> void handleBlockEntity(PoseStack stack,
            E blockEntity,
            float partialTicks,
            SubmitNodeCollector nodeCollector) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(blockEntity);
        var fakeCameraPos = Vec3.atCenterOf(blockEntity.getBlockPos());
        if (renderer != null && renderer.shouldRender(blockEntity, fakeCameraPos)) {
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
            SubmitNodeCollector nodes) {
        for (var entity : level.getEntitiesForRendering()) {
            handleEntity(poseStack, nodes, entity, partialTick);
        }
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
