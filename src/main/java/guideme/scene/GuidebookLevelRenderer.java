package guideme.scene;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexSorting;
import guideme.color.LightDarkMode;
import guideme.scene.annotation.InWorldAnnotation;
import guideme.scene.annotation.InWorldAnnotationRenderer;
import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import net.minecraft.client.renderer.block.model.BakedQuad;
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
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GuidebookLevelRenderer {

    private static GuidebookLevelRenderer instance;

    /**
     * Cached scene geometry is freed when the scene hasn't been rendered for this long.
     */
    private static final long MESH_CACHE_EXPIRY_MS = 30_000;

    private static final int INITIAL_MESH_BUFFER_SIZE = 256 * 1024;

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

        lightmap.update(level);

        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        level.onRenderFrame();

        RenderSystem.setShaderGameTime(level.getGameTime(), level.getPartialTick());

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
        try (var meshCache = new SceneMeshCache()) {
            render(level, cameraSettings, buffers, annotations, lightDarkMode, meshCache);
        }
    }

    private void render(GuidebookLevel level,
            CameraSettings cameraSettings,
            MultiBufferSource.BufferSource buffers,
            Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode,
            SceneMeshCache meshCache) {
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
     * Render without any setup. Since the content is meant to be captured from {@code buffers}, block and fluid
     * geometry is tessellated into them instead of being drawn from a cache.
     */
    public void renderContent(GuidebookLevel level, MultiBufferSource.BufferSource buffers) {
        RenderSystem.runAsFancy(() -> {
            var translucentBlocks = renderBlocks(level, buffers);
            renderBlockEntities(level, buffers, level.getPartialTick());
            renderEntities(level, buffers, level.getPartialTick());
            renderTranslucentBlocks(level, buffers, translucentBlocks);
            buffers.endBatch();
        });
    }

    /**
     * @param meshCache The cache to draw the block and fluid geometry from. It's rebuilt if it's out of date.
     */
    private void renderContent(GuidebookLevel level, MultiBufferSource.BufferSource buffers,
            SceneMeshCache meshCache) {
        RenderSystem.runAsFancy(() -> {
            prepareMeshCache(level, meshCache);
            for (var sprite : meshCache.getAnimatedSprites()) {
                SodiumCompat.markSpriteActive(sprite);
            }
            for (var fluidState : meshCache.getFluids()) {
                markFluidSpritesActive(fluidState);
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
                    drawMeshCache(meshCache, layer);
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

            drawMeshCache(meshCache, RenderType.translucent());
            buffers.endBatch(RenderType.translucent());
        });
    }

    /**
     * Rebuilds the cached block and fluid geometry if it is out of date.
     */
    private static void prepareMeshCache(GuidebookLevel level, SceneMeshCache meshCache) {
        RenderSystem.assertOnRenderThread();

        var key = new SceneMeshCache.Key(
                level.getRevision(),
                Minecraft.useAmbientOcclusion(),
                Minecraft.getInstance().options.graphicsMode().get());
        if (!meshCache.isUpToDate(key)) {
            meshCache.clear();
            buildMeshCache(level, meshCache);
            meshCache.markBuilt(key);
        }
    }

    /**
     * Tessellates the block and fluid geometry into a separate buffer per render type and uploads them to the GPU.
     */
    private static void buildMeshCache(GuidebookLevel level, SceneMeshCache meshCache) {
        var bufferSource = new CapturingBufferSource(meshCache);
        try {
            var translucentBlocks = renderBlocks(level, bufferSource);
            renderTranslucentBlocks(level, bufferSource, translucentBlocks);

            for (var entry : bufferSource.captures.entrySet()) {
                var renderType = entry.getKey();
                var capture = entry.getValue();
                var mesh = capture.builder().build();
                if (mesh == null) {
                    continue; // No vertices
                }

                if (renderType.sortOnUpload()) {
                    // This is what MultiBufferSource.BufferSource would do when drawing it immediately
                    mesh.sortQuads(capture.bytes(), RenderSystem.getVertexSorting());
                }

                var vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                meshCache.addBuffer(renderType, vertexBuffer);
                vertexBuffer.bind();
                vertexBuffer.upload(mesh); // Also closes the mesh
                VertexBuffer.unbind();
            }
        } finally {
            for (var capture : bufferSource.captures.values()) {
                capture.bytes().close();
            }
        }

        // Fluids don't pass their sprites to the vertex consumer, so remember the fluids instead
        level.getFilledBlocks().forEach(pos -> {
            var fluidState = level.getBlockState(pos).getFluidState();
            if (!fluidState.isEmpty()) {
                meshCache.addFluid(fluidState);
            }
        });
    }

    /**
     * Draws the cached geometry for one render type with the current model-view and projection matrices.
     */
    private static void drawMeshCache(SceneMeshCache meshCache, RenderType renderType) {
        var vertexBuffer = meshCache.getBuffer(renderType);
        if (vertexBuffer == null) {
            return;
        }

        renderType.setupRenderState();
        var shader = RenderSystem.getShader();
        if (shader != null) {
            vertexBuffer.bind();
            vertexBuffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
        }
        renderType.clearRenderState();
    }

    /**
     * Captures geometry in a separate buffer per render type and records the animated sprites used by block models.
     */
    private static class CapturingBufferSource implements MultiBufferSource {
        private final Map<RenderType, Capture> captures = new HashMap<>();
        private final SceneMeshCache meshCache;

        CapturingBufferSource(SceneMeshCache meshCache) {
            this.meshCache = meshCache;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return captures.computeIfAbsent(renderType, rt -> {
                var bytes = new ByteBufferBuilder(INITIAL_MESH_BUFFER_SIZE);
                var builder = new BufferBuilder(bytes, rt.mode(), rt.format());
                return new Capture(bytes, builder, new SpriteRecordingVertexConsumer(builder, meshCache));
            }).consumer();
        }
    }

    private record Capture(ByteBufferBuilder bytes, BufferBuilder builder, VertexConsumer consumer) {
    }

    /**
     * {@link ModelBlockRenderer} passes block quads to this {@code putBulkData} overload (the other overload delegates
     * to it), which lets us record their sprites.
     */
    private record SpriteRecordingVertexConsumer(VertexConsumer delegate,
            SceneMeshCache meshCache) implements VertexConsumer {
        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green,
                float blue, float alpha, int[] lightmap, int packedOverlay, boolean readExistingColor) {
            var sprite = quad.getSprite();
            if (sprite.contents().animatedTexture != null) {
                meshCache.addAnimatedSprite(sprite);
            }
            delegate.putBulkData(pose, quad, brightness, red, green, blue, alpha, lightmap, packedOverlay,
                    readExistingColor);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay,
                int packedLight, float normalX, float normalY, float normalZ) {
            delegate.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer misc(VertexFormatElement element, int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }
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
    private static List<DeferredBlock> renderBlocks(GuidebookLevel level, MultiBufferSource buffers) {
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

    private static void renderTranslucentBlocks(GuidebookLevel level, MultiBufferSource buffers,
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
    private record DeferredBlock(BlockPos pos, BlockState state, boolean fluid, boolean block, ModelData modelData) {
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
