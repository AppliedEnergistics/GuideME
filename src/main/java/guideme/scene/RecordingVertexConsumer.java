package guideme.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;

/**
 * Records the vertices written to it, so they can be written to another vertex consumer later with
 * {@link #replay(VertexConsumer)}. Vertices are recorded with all attributes of
 * {@link VertexConsumer#addVertex(float, float, float, int, float, float, int, int, float, float, float)}, which is
 * also how they are replayed.
 */
final class RecordingVertexConsumer implements VertexConsumer {
    /**
     * This is the number of method parameters to the longest overload of {@link VertexConsumer#addVertex}.
     */
    private static final int PARAMS_PER_VERTEX = 11;

    private int[] data = new int[PARAMS_PER_VERTEX * 4];
    private int size;

    // The vertex being built with the chained methods
    private boolean building;
    private float x;
    private float y;
    private float z;
    private int color;
    private float u;
    private float v;
    private int overlayCoords;
    private int lightCoords;
    private float normalX;
    private float normalY;
    private float normalZ;

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        endVertex();
        building = true;
        this.x = x;
        this.y = y;
        this.z = z;
        color = -1;
        u = 0;
        v = 0;
        overlayCoords = OverlayTexture.NO_OVERLAY;
        lightCoords = 0;
        normalX = 0;
        normalY = 1;
        normalZ = 0;
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords,
            float normalX, float normalY, float normalZ) {
        endVertex();
        append(x, y, z, color, u, v, overlayCoords, lightCoords, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        color = ARGB.color(a, r, g, b);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        overlayCoords = (u & 0xFFFF) | (v << 16);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        lightCoords = (u & 0xFFFF) | (v << 16);
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        // Not used by the block vertex formats
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        normalX = x;
        normalY = y;
        normalZ = z;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        // Not used by the block vertex formats
        return this;
    }

    boolean isEmpty() {
        return size == 0 && !building;
    }

    /**
     * Writes all recorded vertices to the given consumer.
     */
    void replay(VertexConsumer consumer) {
        endVertex();
        var data = this.data;
        for (int i = 0; i < size; i += PARAMS_PER_VERTEX) {
            consumer.addVertex(
                    Float.intBitsToFloat(data[i]),
                    Float.intBitsToFloat(data[i + 1]),
                    Float.intBitsToFloat(data[i + 2]),
                    data[i + 3],
                    Float.intBitsToFloat(data[i + 4]),
                    Float.intBitsToFloat(data[i + 5]),
                    data[i + 6],
                    data[i + 7],
                    Float.intBitsToFloat(data[i + 8]),
                    Float.intBitsToFloat(data[i + 9]),
                    Float.intBitsToFloat(data[i + 10]));
        }
    }

    private void endVertex() {
        if (building) {
            building = false;
            append(x, y, z, color, u, v, overlayCoords, lightCoords, normalX, normalY, normalZ);
        }
    }

    private void append(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords,
            float normalX, float normalY, float normalZ) {
        if (size + PARAMS_PER_VERTEX > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        var data = this.data;
        var i = size;
        data[i] = Float.floatToRawIntBits(x);
        data[i + 1] = Float.floatToRawIntBits(y);
        data[i + 2] = Float.floatToRawIntBits(z);
        data[i + 3] = color;
        data[i + 4] = Float.floatToRawIntBits(u);
        data[i + 5] = Float.floatToRawIntBits(v);
        data[i + 6] = overlayCoords;
        data[i + 7] = lightCoords;
        data[i + 8] = Float.floatToRawIntBits(normalX);
        data[i + 9] = Float.floatToRawIntBits(normalY);
        data[i + 10] = Float.floatToRawIntBits(normalZ);
        size += PARAMS_PER_VERTEX;
    }
}
