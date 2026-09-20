package guideme.scene;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import guideme.color.LightDarkMode;
import guideme.internal.scene.FakeRenderEnvironment;
import guideme.scene.annotation.InWorldAnnotation;
import guideme.scene.annotation.InWorldAnnotationRenderer;
import guideme.scene.level.GuidebookLevel;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.Sheets;
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
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders the 3D scenes shown in the guidebook.
 * <p>
 * In 26.3 {@code MultiBufferSource} no longer exists. Geometry is <em>submitted</em> as nodes
 * first, and the actual draw calls only happen later, when the owner of the frame calls
 * {@code FeatureRenderDispatcher#prepareFrame} and {@code FeatureRenderDispatcher#renderAllFeatures}
 * inside a render pass. This has two important consequences for this class:
 * <ul>
 * <li>All global render state this renderer installs (projection matrix, model-view matrix, lightmap,
 * the fake client player from {@code FakeRenderEnvironment}) has to stay installed until the render pass
 * has been executed. It can therefore <em>not</em> be restored by this class when it is used from a
 * picture-in-picture renderer; see
 * {@code guideme.internal.scene.ScenePictureInPictureRenderer#prepare}.</li>
 * <li>Geometry that used to be written directly into a {@code VertexConsumer} is now emitted from within
 * a {@code SubmitNodeCollector.CustomGeometryRenderer} callback that runs during {@code prepareFrame}.</li>
 * </ul>
 */
public class GuidebookLevelRenderer {

    /**
     * How far, in view space units, always-on-top annotations are pushed towards the camera. Because the
     * scene uses an orthographic projection, this is a pure depth bias and does not change the image. The
     * scene's near plane sits at view space z=+1000, so this has to stay well below that.
     */
    private static final float ALWAYS_ON_TOP_DEPTH_BIAS = 500f;

    private static GuidebookLevelRenderer instance;

    private final ProjectionMatrixBuffer projMatBuffer = new ProjectionMatrixBuffer(
            "GuideME level renderer proj mat UBO");

    /**
     * {@code GameRenderer#getGlobalSettingsUniform()} is gone and the field is private. We use our
     * own instance instead; calling {@link GlobalSettingsUniform#update} binds it globally via
     * {@code RenderSystem.setGlobalSettingsUniform}, exactly like the game's own instance does.
     */
    private final GlobalSettingsUniform globalSettings = new GlobalSettingsUniform();

    /** Submit node storage used by {@link #renderToMainTarget}, which drives its own render pass. */
    private final SubmitNodeStorage offscreenSubmitNodes = new SubmitNodeStorage();

    public static GuidebookLevelRenderer getInstance() {
        RenderSystem.assertOnRenderThread();
        if (instance == null) {
            instance = new GuidebookLevelRenderer();
        }
        return instance;
    }

    /**
     * The scene's orthographic projection matrix.
     * <p>
     * 26.3 flipped to a reversed-Z depth buffer (depth is cleared to 0 and the default depth test
     * is {@code GREATER_THAN_OR_EQUAL}). {@code Projection#getMatrix} achieves that by passing its
     * {@code zFar} as JOML's {@code zNear} and vice versa; we do the same with the scene's own near/far
     * planes (-1000 / 3000), and we honour the device's clip space convention.
     */
    public static Matrix4f getProjectionMatrix(CameraSettings cameraSettings) {
        var viewportSize = cameraSettings.getViewportSize();
        var halfWidth = viewportSize.width() / 2f;
        var halfHeight = viewportSize.height() / 2f;
        var zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, 3000f, -1000f, zZeroToOne);
    }

    /**
     * Installs the scene camera (model-view + projection matrix) into the global render state. The caller is
     * responsible for backing up / restoring both, and for keeping them installed until the submitted nodes
     * have actually been drawn.
     */
    public void applyCamera(CameraSettings cameraSettings) {
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.identity();
        modelViewStack.mul(cameraSettings.getViewMatrix());
        RenderSystem.setProjectionMatrix(projMatBuffer.getBuffer(getProjectionMatrix(cameraSettings)),
                ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * Prepares global state and submits the whole scene (blocks, block entities, entities and annotations).
     * <p>
     * The caller must keep the fake render environment, the scene lightmap, the projection matrix and the
     * model-view matrix installed until the submitted nodes have been drawn.
     *
     * @param poseStack The pose the scene is rendered with. It must map scene (block) coordinates directly,
     *                  i.e. the camera is applied through the global model-view matrix, not through this.
     */
    public void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            SubmitNodeCollector collector,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode,
            PoseStack poseStack) {

        level.onRenderFrame();

        var minecraft = Minecraft.getInstance();
        var gameRenderer = minecraft.gameRenderer;

        // GlobalSettingsUniform#update takes the partial tick directly now instead of a DeltaTracker.
        globalSettings.update(
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

        // Essentially disable level fog
        RenderSystem.setShaderFog(gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));

        applyCamera(cameraSettings);

        // GameRenderer#getLighting() -> GameRenderer#lighting()
        gameRenderer.lighting().updateLevel(CardinalLighting.Type.DEFAULT);
        gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);

        renderContent(level, collector, poseStack);

        InWorldAnnotationRenderer.render(collector, poseStack, annotations, lightDarkMode,
                getAlwaysOnTopOffset(cameraSettings));
    }

    /**
     * World space offset that moves geometry {@link #ALWAYS_ON_TOP_DEPTH_BIAS} units towards the camera.
     */
    private static Vector3f getAlwaysOnTopOffset(CameraSettings cameraSettings) {
        var offset = new Vector3f(0, 0, ALWAYS_ON_TOP_DEPTH_BIAS);
        new Matrix4f(cameraSettings.getViewMatrix()).invert().transformDirection(offset);
        return offset;
    }

    /**
     * Submits the scene content (blocks, block entities, entities) without any camera or global state setup.
     * <p>
     * This no longer renders anything by itself. A {@link FakeRenderEnvironment} must be open both
     * while this method runs (block entity and entity render states are extracted here) <em>and</em> while
     * the resulting submit nodes are prepared, because the block geometry is tessellated lazily from within
     * the custom-geometry callbacks.
     */
    public void renderContent(GuidebookLevel level, SubmitNodeCollector collector, PoseStack poseStack) {
        submitBlocks(level, collector, poseStack, false);
        submitBlockEntities(level, collector, level.getPartialTick(), poseStack);
        submitEntities(level, level.getPartialTick(), poseStack, collector);
        submitBlocks(level, collector, poseStack, true);
    }

    /**
     * Renders the scene into the currently bound main render target, driving the whole submit / prepare /
     * render pass cycle. Used by the off-screen (site export) renderer, which - unlike the in-game
     * picture-in-picture path - owns its render target.
     */
    public void renderToMainTarget(GuidebookLevel level,
            CameraSettings cameraSettings,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode) {
        var minecraft = Minecraft.getInstance();
        var gameRenderer = minecraft.gameRenderer;
        var target = gameRenderer.mainRenderTarget();

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        RenderSystem.backupProjectionMatrix();

        var previousUseUiLightmap = gameRenderer.useUiLightmap;
        gameRenderer.useUiLightmap = false;
        var lightmapRenderState = new LightmapRenderState();
        lightmapRenderState.needsUpdate = true;
        gameRenderer.lightmap.render(lightmapRenderState);

        try (var ignored = FakeRenderEnvironment.create(level)) {
            render(level, cameraSettings, offscreenSubmitNodes, annotations, lightDarkMode, new PoseStack());

            var dispatcher = gameRenderer.featureRenderDispatcher();
            try (var frame = dispatcher.prepareFrame(offscreenSubmitNodes);
                    var renderPass = RenderSystem.getDevice()
                            .createCommandEncoder()
                            .createRenderPass(() -> "GuideME scene",
                                    Objects.requireNonNull(target.getColorTextureView(), "colorTextureView"),
                                    Optional.empty(),
                                    target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(renderPass);
                FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
            }
        } finally {
            gameRenderer.useUiLightmap = previousUseUiLightmap;
            gameRenderer.gameRenderState().lightmapRenderState.needsUpdate = true;
            gameRenderer.lightmap.render(gameRenderer.gameRenderState().lightmapRenderState);
            RenderSystem.restoreProjectionMatrix();
            modelViewStack.popMatrix();
        }
    }

    // Sheets.cutoutBlockSheet() -> Sheets.cutoutBlockItemSheet(),
    // Sheets.translucentBlockSheet() -> Sheets.translucentBlockItemSheet(). In 26.3 the block-atlas variants
    // are named "*BlockItemSheet" and the item-atlas variants "*ItemSheet".
    private static RenderType getEntityRenderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID, CUTOUT -> Sheets.cutoutBlockItemSheet();
            case TRANSLUCENT -> Sheets.translucentBlockItemSheet();
        };
    }

    private void submitBlocks(GuidebookLevel level, SubmitNodeCollector collector, PoseStack poseStack,
            boolean translucent) {
        var renderType = getEntityRenderType(translucent ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.SOLID);
        collector.submitCustomGeometry(poseStack, renderType,
                (pose, buffer) -> tesselateBlocks(level, pose, buffer, translucent));
    }

    private static void tesselateBlocks(GuidebookLevel level, PoseStack.Pose basePose,
            VertexConsumer buffer, boolean translucent) {
        var minecraft = Minecraft.getInstance();
        boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
        var blockRenderer = new ModelBlockRenderer(ambientOcclusion, false, minecraft.getBlockColors());
        var modelManager = minecraft.getModelManager();
        var fluidModelSet = modelManager.getFluidStateModelSet();
        var fluidRenderer = new FluidRenderer(fluidModelSet);

        var poseStack = new PoseStack();
        poseStack.last().set(basePose);

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            var layer = quad.materialInfo().layer();
            if (layer.translucent() == translucent) {
                buffer.putBakedQuad(poseStack.last(), quad, instance);
            }
        };

        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            var fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                var sectionPos = SectionPos.of(pos);
                // Note: as in 26.1, the fluid renderer writes absolute scene coordinates and ignores the
                // pose; the scene camera is applied through the global model-view matrix instead.
                FluidRenderer.Output fluidOutput = layer -> {
                    if (layer.translucent() == translucent) {
                        return new LiquidVertexConsumer(buffer, sectionPos);
                    } else {
                        return NoopVertexConsumer.INSTANCE;
                    }
                };

                var customRenderer = fluidModelSet.get(fluidState).customRenderer();
                if (customRenderer == null
                        || !customRenderer.renderFluid(fluidRenderer, fluidState, level, pos, fluidOutput,
                                blockState)) {
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

    private void submitBlockEntities(GuidebookLevel level, SubmitNodeCollector collector, float partialTick,
            PoseStack poseStack) {
        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var blockState = level.getBlockState(pos);
            if (blockState.hasBlockEntity()) {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.handleBlockEntity(poseStack, blockEntity, partialTick, collector);
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
        // BlockPos#getCenter() was removed -> Vec3.atCenterOf(BlockPos)
        if (renderer != null && renderer.shouldRender(blockEntity, Vec3.atCenterOf(blockEntity.getBlockPos()))) {
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

    private void submitEntities(GuidebookLevel level,
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector collector) {
        for (var entity : level.getEntitiesForRendering()) {
            handleEntity(poseStack, collector, entity, partialTick);
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
        var pos = entity.position();
        var state = renderer.createRenderState(entity, partialTicks);
        var offset = renderer.getRenderOffset(state);
        poseStack.pushPose();
        poseStack.translate(pos.x + offset.x(), pos.y + offset.y(), pos.z + offset.z());
        renderer.submit(state, poseStack, submitNodeCollector, new CameraRenderState());
        poseStack.popPose();
    }
}
