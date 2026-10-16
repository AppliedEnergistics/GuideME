package guideme.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

class RecordingVertexConsumerTest {
    @Test
    void testEmpty() {
        var recorder = new RecordingVertexConsumer();
        assertTrue(recorder.isEmpty());

        var output = new CapturingVertexConsumer();
        recorder.replay(output);
        assertEquals(List.of(), output.vertices);
    }

    @Test
    void testReplaysFullVerticesExactly() {
        var recorder = new RecordingVertexConsumer();
        var first = new Vertex(1.5f, -2.25f, 3.125f, 0x80FF4020, 0.25f, 0.75f, 0x000A0003, 0x00F000F0, 0, 1, 0);
        var second = new Vertex(Float.MIN_VALUE, -0.0f, 1e9f, -1, 1, 0, OverlayTexture.NO_OVERLAY, 0, 0.6f, 0, -0.8f);
        first.addTo(recorder);
        second.addTo(recorder);
        assertFalse(recorder.isEmpty());

        var output = new CapturingVertexConsumer();
        recorder.replay(output);
        assertEquals(List.of(first, second), output.vertices);
    }

    @Test
    void testRecordsChainedVertices() {
        var recorder = new RecordingVertexConsumer();
        recorder.addVertex(1, 2, 3)
                .setColor(0x10, 0x20, 0x30, 0x40)
                .setUv(0.5f, 0.25f)
                .setLight(0x00D000C0)
                .setOverlay(0x00050006)
                .setNormal(0, 0, 1);
        // The last vertex has no further addVertex call ending it
        recorder.addVertex(4, 5, 6).setColor(0xFF00FF00);
        assertFalse(recorder.isEmpty());

        var output = new CapturingVertexConsumer();
        recorder.replay(output);
        assertEquals(List.of(
                new Vertex(1, 2, 3, ARGB.color(0x40, 0x10, 0x20, 0x30), 0.5f, 0.25f, 0x00050006, 0x00D000C0, 0, 0, 1),
                new Vertex(4, 5, 6, 0xFF00FF00, 0, 0, OverlayTexture.NO_OVERLAY, 0, 0, 1, 0)),
                output.vertices);
    }

    @Test
    void testReplayCanBeRepeated() {
        var recorder = new RecordingVertexConsumer();
        recorder.addVertex(1, 2, 3).setColor(-1);
        new Vertex(4, 5, 6, -1, 0, 0, 0, 0, 0, 1, 0).addTo(recorder);

        var firstOutput = new CapturingVertexConsumer();
        recorder.replay(firstOutput);
        var secondOutput = new CapturingVertexConsumer();
        recorder.replay(secondOutput);
        assertEquals(2, firstOutput.vertices.size());
        assertEquals(firstOutput.vertices, secondOutput.vertices);
    }

    @Test
    void testGrowsBeyondInitialCapacity() {
        var recorder = new RecordingVertexConsumer();
        var expected = new ArrayList<Vertex>();
        for (int i = 0; i < 1000; i++) {
            var vertex = new Vertex(i, -i, i * 0.5f, i, i / 1000f, 1 - i / 1000f, i, i * 2, 0, 1, 0);
            vertex.addTo(recorder);
            expected.add(vertex);
        }

        var output = new CapturingVertexConsumer();
        recorder.replay(output);
        assertEquals(expected, output.vertices);
    }

    /**
     * Records compare float components with {@link Float#compare}, so -0.0f and 0.0f are different.
     */
    private record Vertex(float x, float y, float z, int color, float u, float v, int overlayCoords,
            int lightCoords, float normalX, float normalY, float normalZ) {
        void addTo(VertexConsumer consumer) {
            consumer.addVertex(x, y, z, color, u, v, overlayCoords, lightCoords, normalX, normalY, normalZ);
        }
    }

    /**
     * Only accepts complete vertices, which is how {@link RecordingVertexConsumer#replay} writes them.
     */
    private static class CapturingVertexConsumer implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords,
                int lightCoords, float normalX, float normalY, float normalZ) {
            vertices.add(new Vertex(x, y, z, color, u, v, overlayCoords, lightCoords, normalX, normalY, normalZ));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setColor(int color) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            throw new UnsupportedOperationException();
        }
    }
}
