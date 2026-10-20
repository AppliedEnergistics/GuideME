package guideme.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import guideme.scene.level.GuidebookLevel;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the block and fluid geometry of a {@link GuidebookLevel} in GPU vertex buffers, so that it doesn't have to be
 * tessellated again on every frame. The geometry is built and drawn by {@link GuidebookLevelRenderer}.
 * <p>
 * Must only be used on the render thread.
 */
final class SceneMeshCache implements AutoCloseable {
    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    /**
     * Animated sprites and fluids used by the cached geometry. Since the geometry is not re-emitted every frame, their
     * sprites have to be marked as active explicitly for Sodium to keep animating them.
     */
    private final Set<TextureAtlasSprite> animatedSprites = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Fluid, FluidState> fluids = new HashMap<>();
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
     * Frees the cached geometry before it is built again. The cache is out of date until {@link #markBuilt} is called,
     * so that a build that fails halfway isn't used.
     */
    void clear() {
        for (var vertexBuffer : buffers.values()) {
            vertexBuffer.close();
        }
        buffers.clear();
        animatedSprites.clear();
        fluids.clear();
        key = null;
    }

    void markBuilt(Key key) {
        this.key = key;
    }

    /**
     * Takes ownership of the given vertex buffer.
     */
    void addBuffer(RenderType renderType, VertexBuffer vertexBuffer) {
        var previous = buffers.put(renderType, vertexBuffer);
        if (previous != null) {
            previous.close();
        }
    }

    @Nullable
    VertexBuffer getBuffer(RenderType renderType) {
        return buffers.get(renderType);
    }

    void addAnimatedSprite(TextureAtlasSprite sprite) {
        animatedSprites.add(sprite);
    }

    Set<TextureAtlasSprite> getAnimatedSprites() {
        return animatedSprites;
    }

    void addFluid(FluidState fluidState) {
        fluids.putIfAbsent(fluidState.getType(), fluidState);
    }

    Collection<FluidState> getFluids() {
        return fluids.values();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        clear();
    }

    /**
     * The geometry is rebuilt when the level's {@linkplain GuidebookLevel#getRevision() revision} changes, or when
     * smooth lighting or the graphics mode changes. Model data that a block entity changes without a block change is
     * not picked up until the next rebuild.
     */
    record Key(int levelRevision,
            boolean ambientOcclusion,
            GraphicsStatus graphicsMode) {
    }
}
