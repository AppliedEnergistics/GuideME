package guideme.scene.export;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector4i;

/**
 * Captured rendering data.
 *
 * @param indexBuffer Can be null if sequential indices are to be used.
 */
record Mesh(MeshData.DrawState drawState,
        ByteBuffer vertexBuffer,
        @Nullable ByteBuffer indexBuffer,
        RenderType renderType) {

    /**
     * Checks if the mesh contains any texture atlases that are animated.
     */
    public Stream<TextureAtlasSprite> getSprites() {

        var textureManager = Minecraft.getInstance().getTextureManager();

        // We only implement this for quads since standard block vertex-format
        // uses BakedQuads
        if (drawState.primitiveTopology() != PrimitiveTopology.QUADS) {
            return Stream.of();
        }

        // Get texture bound to sampler 0
        var samplers = RenderTypeIntrospection.getSamplers(renderType);
        if (samplers.isEmpty()) {
            return Stream.of(); // No textures
        }
        var texture = textureManager.getTexture(samplers.get(0).texture());
        if (!(texture instanceof TextureAtlas textureAtlas)) {
            return Stream.of(); // Only atlases can have sprites
        }

        // VertexFormatElement is now a record (name, offset, GpuFormat) looked up by
        // semantic name, and it carries its own byte offset - so the manual offset walk is gone.
        var uvElement = renderType.format().getElement(DefaultVertexFormat.UV0_SEMANTIC_NAME);
        if (uvElement == null || uvElement.format().componentCount() != 2) {
            return Stream.of(); // No UV coordinates
        }

        var uvSupplier = getUvSupplier(uvElement.offset(), uvElement);
        // TODO: Should cache this...
        var spriteFinder = new SpriteFinder(textureAtlas.getTextures(), textureAtlas);

        return streamQuadMidpoints(uvSupplier)
                .map(uvPos -> spriteFinder.find(uvPos.x, uvPos.y))
                .filter(Objects::nonNull);
    }

    private Stream<Vector2f> streamQuadMidpoints(IntFunction<Vector2f> uvSupplier) {
        return streamIndices().map(indices -> getQuadMidpoint(indices.x, indices.y, indices.z, indices.w, uvSupplier));
    }

    private Stream<Vector4i> streamIndices() {
        if (indexBuffer == null) {
            var quadCount = drawState.vertexCount() / 4;
            return IntStream.range(0, quadCount)
                    .mapToObj(quadIdx -> new Vector4i(quadIdx * 4, quadIdx * 4 + 1, quadIdx * 4 + 2, quadIdx * 4 + 3));
        } else if (drawState.indexType() == IndexType.INT) {
            var quadCount = drawState.indexCount() / 4;
            return IntStream.range(0, quadCount)
                    .mapToObj(quadIdx -> new Vector4i(
                            indexBuffer.getInt(quadIdx * 4 * 4),
                            indexBuffer.getInt(quadIdx * 4 * 4 + 4),
                            indexBuffer.getInt(quadIdx * 4 * 4 + 8),
                            indexBuffer.getInt(quadIdx * 4 * 4 + 12)));
        } else if (drawState.indexType() == IndexType.SHORT) {
            var quadCount = drawState.indexCount() / 4;
            return IntStream.range(0, quadCount)
                    .mapToObj(quadIdx -> new Vector4i(
                            indexBuffer.getShort(quadIdx * 4 * 2),
                            indexBuffer.getShort(quadIdx * 4 * 2 + 2),
                            indexBuffer.getShort(quadIdx * 4 * 2 + 4),
                            indexBuffer.getShort(quadIdx * 4 * 2 + 6)));
        } else {
            throw new IllegalArgumentException("Unsupported index type: " + drawState.indexType());
        }
    }

    private IntFunction<Vector2f> getUvSupplier(int offset, VertexFormatElement uvElement) {
        return idx -> getUV(idx, offset, uvElement);
    }

    private Vector2f getQuadMidpoint(int i1, int i2, int i3, int i4, IntFunction<Vector2f> uvSupplier) {
        var uv1 = uvSupplier.apply(i1);
        var uv2 = uvSupplier.apply(i2);
        var uv3 = uvSupplier.apply(i3);
        var uv4 = uvSupplier.apply(i4);
        // We're making the assumption that a rectangle of the texture is used since
        // all atlas-entries are rectangular
        var avgX = (uv1.x + uv2.x + uv3.x + uv4.x) / 4f;
        var avgY = (uv1.y + uv2.y + uv3.y + uv4.y) / 4f;
        return new Vector2f(avgX, avgY);
    }

    private Vector2f getUV(int index, int offset, VertexFormatElement uvElement) {
        var stride = drawState.format().getVertexSize();
        var dataStart = index * stride + offset;
        var componentType = uvElement.format().componentType();
        return new Vector2f(
                readFloat(componentType, dataStart),
                readFloat(componentType, dataStart + componentType.byteSize()));
    }

    // VertexFormatElement.Type was replaced by GpuFormat.ComponentType, which splits the
    // old types by normalisation (UNORM/SNORM vs UINT/SINT) and adds half-float and opaque kinds.
    private float readFloat(GpuFormat.ComponentType type, int offset) {
        return switch (type) {
            case FLOAT_32 -> vertexBuffer.getFloat(offset);
            case UNORM_8, UINT_8 -> ((int) vertexBuffer.get(offset)) & 0xFF;
            case SNORM_8, SINT_8 -> vertexBuffer.get(offset);
            case UNORM_16, UINT_16 -> ((int) vertexBuffer.getShort(offset)) & 0xFFFF;
            case SNORM_16, SINT_16 -> vertexBuffer.getShort(offset);
            case UINT_32, SINT_32 -> vertexBuffer.getInt(offset);
            default -> throw new IllegalArgumentException("Unsupported UV component type: " + type);
        };
    }
}
