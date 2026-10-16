package guideme.scene;

import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the tessellated block and fluid geometry of a {@link GuidebookLevel}, so that it doesn't have to be tessellated
 * again on every frame. The geometry is built and submitted by {@link GuidebookLevelRenderer}, and replayed by
 * {@link SceneMeshFeatureRenderer}.
 * <p>
 * Non-translucent geometry is kept per chunk layer for the whole scene. Translucent geometry is kept per block, so it
 * can be sorted together with the translucent geometry of block entities and entities.
 * <p>
 * Must only be used on the render thread.
 */
final class SceneMeshCache {
    private final Map<ChunkSectionLayer, RecordingVertexConsumer> opaqueLayers = new EnumMap<>(ChunkSectionLayer.class);
    private final List<TranslucentBlock> translucentBlocks = new ArrayList<>();
    /**
     * Animated sprites used by the cached geometry. Since the geometry is not tessellated every frame, they have to be
     * marked as active explicitly for Sodium to keep animating them.
     */
    private final Set<TextureAtlasSprite> animatedSprites = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * What the cached geometry was built for, or null if it was never completely built.
     */
    @Nullable
    private Key key;
    long lastUsed;

    boolean isUpToDate(Key key) {
        return key.equals(this.key);
    }

    /**
     * Discards the cached geometry before it is built again. The cache is out of date until {@link #markBuilt} is
     * called, so that a build that fails halfway isn't used.
     */
    void clear() {
        opaqueLayers.clear();
        translucentBlocks.clear();
        animatedSprites.clear();
        key = null;
    }

    void markBuilt(Key key) {
        this.key = key;
    }

    RecordingVertexConsumer getOpaqueLayer(ChunkSectionLayer layer) {
        return opaqueLayers.computeIfAbsent(layer, ignored -> new RecordingVertexConsumer());
    }

    Map<ChunkSectionLayer, RecordingVertexConsumer> getOpaqueLayers() {
        return opaqueLayers;
    }

    void addTranslucentBlock(BlockPos pos, RecordingVertexConsumer geometry) {
        translucentBlocks.add(new TranslucentBlock(pos, geometry));
    }

    List<TranslucentBlock> getTranslucentBlocks() {
        return translucentBlocks;
    }

    void addAnimatedSprite(TextureAtlasSprite sprite) {
        animatedSprites.add(sprite);
    }

    Set<TextureAtlasSprite> getAnimatedSprites() {
        return animatedSprites;
    }

    record TranslucentBlock(BlockPos pos, RecordingVertexConsumer geometry) {
    }

    /**
     * The geometry is rebuilt when the level's {@linkplain GuidebookLevel#getRevision() revision} changes, when smooth
     * lighting, cutout leaves or NeoForge's per-part ambient occlusion setting changes, or when the block or fluid
     * model sets are replaced by a resource reload.
     */
    record Key(int levelRevision,
            boolean ambientOcclusion,
            boolean cutoutLeaves,
            boolean ambientOcclusionPerPart,
            BlockStateModelSet blockModels,
            FluidStateModelSet fluidModels) {
    }
}
