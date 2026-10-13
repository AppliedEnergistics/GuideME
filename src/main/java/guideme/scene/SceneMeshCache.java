package guideme.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.config.NeoForgeClientConfig;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the tessellated block and fluid geometry of a {@link GuidebookLevel}, so that it doesn't have to be tessellated
 * again on every frame. The geometry is recorded the same way {@link BlockModelFeatureRenderer} and
 * {@link FluidModelFeatureRenderer} write it, and replayed by {@link SceneMeshFeatureRenderer}.
 * <p>
 * Non-translucent geometry is kept per chunk layer for the whole scene. Translucent geometry is kept per block, and
 * submitted per block like it is without the cache, so it is still sorted together with the translucent geometry of
 * block entities and entities.
 * <p>
 * The geometry is rebuilt when the level's {@linkplain GuidebookLevel#getRevision() revision} changes, when smooth
 * lighting, cutout leaves or NeoForge's per-part ambient occlusion setting changes, or when the block or fluid model
 * sets are replaced by a resource reload.
 * <p>
 * Must only be used on the render thread.
 */
final class SceneMeshCache {
    private final Map<ChunkSectionLayer, RecordingVertexConsumer> opaqueLayers = new EnumMap<>(ChunkSectionLayer.class);
    private final List<SceneMeshFeatureRenderer.Submit> translucentBlocks = new ArrayList<>();
    /**
     * Animated sprites used by the cached geometry. Since the geometry is not tessellated every frame, they have to be
     * marked as active explicitly for Sodium to keep animating them.
     */
    private final Set<TextureAtlasSprite> animatedSprites = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable
    private Key key;
    long lastUsed;

    /**
     * Rebuilds the cached geometry if it is out of date.
     */
    void prepare(GuidebookLevel level) {
        RenderSystem.assertOnRenderThread();

        var minecraft = Minecraft.getInstance();
        // The same options the feature renderers use when tessellating without the cache
        var options = minecraft.gameRenderer.gameRenderState().optionsRenderState;
        var modelManager = minecraft.getModelManager();
        var currentKey = new Key(
                level.getRevision(),
                options.ambientOcclusion,
                options.cutoutLeaves,
                NeoForgeClientConfig.INSTANCE.handleAmbientOcclusionPerPart.getAsBoolean(),
                modelManager.getBlockStateModelSet(),
                modelManager.getFluidStateModelSet());
        if (!currentKey.equals(key)) {
            build(level, options, currentKey.blockModels(), currentKey.fluidModels());
            key = currentKey;
        }
    }

    /**
     * Submits the cached geometry for rendering. The geometry was recorded in level coordinates, so this is only
     * correct where blocks would otherwise be submitted with an identity pose, like {@link GuidebookLevelRenderer} does
     * when rendering a scene.
     */
    void submit(SubmitNodeCollector nodes) {
        if (!opaqueLayers.isEmpty()) {
            var layers = new ArrayList<SceneMeshFeatureRenderer.Layer>(opaqueLayers.size());
            for (var entry : opaqueLayers.entrySet()) {
                layers.add(new SceneMeshFeatureRenderer.Layer(entry.getKey(), entry.getValue()));
            }
            nodes.submitSpecial(RenderPhaseKeys.SOLID,
                    new SceneMeshFeatureRenderer.Submit(new PoseStack().last(), layers));
        }
        for (var translucentBlock : translucentBlocks) {
            nodes.submitSpecial(RenderPhaseKeys.TRANSLUCENT_BLOCKS_AND_ITEMS, translucentBlock);
        }
    }

    void markSpritesActive() {
        for (var sprite : animatedSprites) {
            SodiumCompat.markSpriteActive(sprite);
        }
    }

    private void build(GuidebookLevel level, OptionsRenderState options, BlockStateModelSet blockModels,
            FluidStateModelSet fluidModels) {
        opaqueLayers.clear();
        translucentBlocks.clear();
        animatedSprites.clear();

        var blockRenderer = new ModelBlockRenderer(options.ambientOcclusion, true,
                Minecraft.getInstance().getBlockColors());
        var fluidRenderer = new FluidRenderer(fluidModels);
        var poseStack = new PoseStack();
        var block = new BlockGeometry();

        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next().immutable();
            var blockState = level.getBlockState(pos);
            block.start(pos);

            // Mirrors GuidebookLevelRenderer#renderBlocks and the feature renderers it submits to
            var fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                var fluidModel = fluidModels.get(fluidState);
                addAnimatedSprites(fluidModel);

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
                // Without the cache, translucent quads are only drawn for models with this flag
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
                        animatedSprites.add(sprite);
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

    private void addAnimatedSprites(FluidModel fluidModel) {
        addAnimatedSprite(fluidModel.stillMaterial());
        addAnimatedSprite(fluidModel.flowingMaterial());
        addAnimatedSprite(fluidModel.overlayMaterial());
    }

    private void addAnimatedSprite(Material.@Nullable Baked material) {
        if (material != null && material.sprite().isAnimated()) {
            animatedSprites.add(material.sprite());
        }
    }

    /**
     * Collects the geometry of one block: non-translucent geometry goes to the scene-wide layers, translucent geometry
     * is kept for the block.
     */
    private final class BlockGeometry {
        private BlockPos pos = BlockPos.ZERO;
        @Nullable
        private RecordingVertexConsumer translucent;

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
            return opaqueLayers.computeIfAbsent(layer, ignored -> new RecordingVertexConsumer());
        }

        void finish() {
            if (translucent != null && !translucent.isEmpty()) {
                var pose = new PoseStack();
                pose.translate(pos.getX(), pos.getY(), pos.getZ());
                translucentBlocks.add(new SceneMeshFeatureRenderer.Submit(pose.last(), List.of(
                        new SceneMeshFeatureRenderer.Layer(ChunkSectionLayer.TRANSLUCENT, translucent))));
            }
        }
    }

    private record Key(int levelRevision,
            boolean ambientOcclusion,
            boolean cutoutLeaves,
            boolean ambientOcclusionPerPart,
            BlockStateModelSet blockModels,
            FluidStateModelSet fluidModels) {
    }
}
