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
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public class GuidebookLevelRenderer {

    private static GuidebookLevelRenderer instance;

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
            LightDarkMode lightDarkMode,
            SubmitNodeCollector nodes, PoseStack poseStack) {

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
            renderContent(level, ns, new PoseStack());

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
     * Render without any setup.
     */
    public void renderContent(GuidebookLevel level, SubmitNodeCollector nodes,
            PoseStack poseStack) {
        var featureRenderDispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();

        try (var fake = FakeRenderEnvironment.create(getFakePlayer())) {
            renderBlocks(level, nodes, poseStack);
            renderBlockEntities(level, level.getPartialTick(), poseStack, nodes);
            renderEntities(level, level.getPartialTick(), poseStack, nodes);
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

    private void renderBlocks(GuidebookLevel level, SubmitNodeCollector nodes, PoseStack poseStack) {
        var modelManager = Minecraft.getInstance().getModelManager();
        var fluidModelSet = modelManager.getFluidStateModelSet();

        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            var fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                // The fluid renderer emits vertices relative to the chunk section origin
                var sectionPos = SectionPos.of(pos);
                poseStack.pushPose();
                poseStack.translate(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ());
                // Tessellation is deferred until the submits are rendered, so we cannot hold on to the mutable pos
                var submit = new FluidModelFeatureRenderer.Submit(level, pos.immutable(), poseStack.last().copy(),
                        blockState, fluidState);
                poseStack.popPose();

                if (fluidModelSet.get(fluidState).layer().translucent()) {
                    nodes.submitSpecial(RenderPhaseKeys.TRANSLUCENT_BLOCKS_AND_ITEMS, submit);
                } else {
                    nodes.submitSpecial(RenderPhaseKeys.SOLID, submit);
                }
            }

            if (blockState.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }

            var model = modelManager.getBlockStateModelSet().get(blockState);

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            // Tessellation is deferred until the submits are rendered, so we cannot hold on to the mutable pos
            var immutablePos = pos.immutable();
            nodes.submitSpecial(RenderPhaseKeys.SOLID, new BlockModelFeatureRenderer.Submit(level, immutablePos,
                    poseStack.last().copy(), blockState, false));
            // Translucent quads have to be rendered after everything else, and are sorted back-to-front
            if (model.hasMaterialFlag(level, pos, blockState, BakedQuad.FLAG_TRANSLUCENT)) {
                nodes.submitSpecial(RenderPhaseKeys.TRANSLUCENT_BLOCKS_AND_ITEMS,
                        new BlockModelFeatureRenderer.Submit(level, immutablePos, poseStack.last().copy(), blockState,
                                true));
            }
            poseStack.popPose();
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
