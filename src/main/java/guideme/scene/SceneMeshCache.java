package guideme.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import guideme.scene.level.GuidebookLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the block and fluid geometry of a {@link GuidebookLevel} in GPU vertex buffers, so that it doesn't have to be
 * tessellated again on every frame. It is rebuilt when the level's revision changes, or when smooth lighting or the
 * graphics mode changes. Model data that a block entity changes without a block change is not picked up until the next
 * rebuild.
 * <p>
 * Must only be used on the render thread.
 */
final class SceneMeshCache implements AutoCloseable {
    private static final int INITIAL_BUFFER_SIZE = 256 * 1024;

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    /**
     * Animated sprites and fluids used by the cached geometry. Since the geometry is not re-emitted every frame, their
     * sprites have to be marked as active explicitly for Sodium to keep animating them.
     */
    private final List<TextureAtlasSprite> animatedSprites = new ArrayList<>();
    private final Map<Fluid, FluidState> fluids = new HashMap<>();
    @Nullable
    private Key key;
    long lastUsed;

    /**
     * Rebuilds the cached geometry if it is out of date.
     */
    void prepare(GuidebookLevel level) {
        RenderSystem.assertOnRenderThread();

        var currentKey = new Key(
                level.getRevision(),
                Minecraft.useAmbientOcclusion(),
                Minecraft.getInstance().options.graphicsMode().get());
        if (!currentKey.equals(key)) {
            build(level);
            key = currentKey;
        }
    }

    private void build(GuidebookLevel level) {
        freeBuffers();

        var sprites = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());
        var bufferSource = new CapturingBufferSource(sprites);
        try {
            var translucentBlocks = GuidebookLevelRenderer.renderBlocks(level, bufferSource);
            GuidebookLevelRenderer.renderTranslucentBlocks(level, bufferSource, translucentBlocks);

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
                buffers.put(renderType, vertexBuffer);
                vertexBuffer.bind();
                vertexBuffer.upload(mesh); // Also closes the mesh
                VertexBuffer.unbind();
            }
        } finally {
            for (var capture : bufferSource.captures.values()) {
                capture.bytes().close();
            }
        }

        for (var sprite : sprites) {
            if (sprite.contents().animatedTexture != null) {
                animatedSprites.add(sprite);
            }
        }
        // Fluids don't pass their sprites to the vertex consumer, so remember the fluids instead
        level.getFilledBlocks().forEach(pos -> {
            var fluidState = level.getBlockState(pos).getFluidState();
            if (!fluidState.isEmpty()) {
                fluids.putIfAbsent(fluidState.getType(), fluidState);
            }
        });
    }

    /**
     * Draws the cached geometry for one render type with the current model-view and projection matrices.
     */
    void draw(RenderType renderType) {
        var vertexBuffer = buffers.get(renderType);
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

    void markSpritesActive() {
        for (var sprite : animatedSprites) {
            SodiumCompat.markSpriteActive(sprite);
        }
        for (var fluidState : fluids.values()) {
            GuidebookLevelRenderer.markFluidSpritesActive(fluidState);
        }
    }

    private void freeBuffers() {
        for (var vertexBuffer : buffers.values()) {
            vertexBuffer.close();
        }
        buffers.clear();
        animatedSprites.clear();
        fluids.clear();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        freeBuffers();
    }

    private record Key(int levelRevision,
            boolean ambientOcclusion,
            GraphicsStatus graphicsMode) {
    }

    /**
     * Captures geometry in a separate buffer per render type and records the sprites used by block models.
     */
    private static class CapturingBufferSource implements MultiBufferSource {
        private final Map<RenderType, Capture> captures = new HashMap<>();
        private final Set<TextureAtlasSprite> sprites;

        CapturingBufferSource(Set<TextureAtlasSprite> sprites) {
            this.sprites = sprites;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return captures.computeIfAbsent(renderType, rt -> {
                var bytes = new ByteBufferBuilder(INITIAL_BUFFER_SIZE);
                var builder = new BufferBuilder(bytes, rt.mode(), rt.format());
                return new Capture(bytes, builder, new SpriteRecordingVertexConsumer(builder, sprites));
            }).consumer();
        }
    }

    private record Capture(ByteBufferBuilder bytes, BufferBuilder builder, VertexConsumer consumer) {
    }

    /**
     * {@link net.minecraft.client.renderer.block.ModelBlockRenderer} passes block quads to this {@code putBulkData}
     * overload (the other overload delegates to it), which lets us record their sprites.
     */
    private record SpriteRecordingVertexConsumer(VertexConsumer delegate,
            Set<TextureAtlasSprite> sprites) implements VertexConsumer {
        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green,
                float blue, float alpha, int[] lightmap, int packedOverlay, boolean readExistingColor) {
            sprites.add(quad.getSprite());
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
}
