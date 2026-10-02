package guideme.scene.export;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import guideme.internal.hooks.VertexCaptureHooks;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Captures all 3D geometry rendered by feature renderers into buffers suitable for export, grouped by render type.
 */
class MeshBuildingBufferSource implements AutoCloseable {
    private final Map<RenderType, ByteBufferBuilder> buffers = new IdentityHashMap<>();
    // Keep the order in which render types were first used, since within a render phase, it is also the order they'd
    // be drawn in. Feature renderers are prepared feature by feature though, not phase by phase.
    private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();

    /**
     * Captures all geometry emitted by feature renderers while running the given code.
     */
    public void captureDuring(Runnable runnable) {
        VertexCaptureHooks.captureDuring(this::getBuffer, runnable);
    }

    private VertexConsumer getBuffer(RenderType renderType) {
        return builders.computeIfAbsent(renderType, type -> {
            var buffer = buffers.computeIfAbsent(type, ignored -> new ByteBufferBuilder(786432));
            return new BufferBuilder(buffer, type.primitiveTopology(), type.format());
        });
    }

    /**
     * Builds the meshes from all geometry captured so far.
     */
    public List<Mesh> getMeshes() {
        var meshes = new ArrayList<Mesh>();

        for (var entry : builders.entrySet()) {
            var renderType = entry.getKey();
            try (var meshData = entry.getValue().build()) {
                if (meshData == null) {
                    continue;
                }

                var vbSource = meshData.vertexBuffer();
                var vertexBuffer = ByteBuffer.allocate(vbSource.remaining())
                        .order(ByteOrder.nativeOrder());
                vertexBuffer.put(vbSource);
                vertexBuffer.flip();

                // Copy the index buffer
                ByteBuffer indexBuffer = null;
                var ibSource = meshData.indexBuffer();
                if (ibSource != null) {
                    indexBuffer = ByteBuffer.allocate(ibSource.remaining()).order(ByteOrder.nativeOrder());
                    indexBuffer.put(ibSource);
                    indexBuffer.flip();
                }

                meshes.add(new Mesh(meshData.drawState(), vertexBuffer, indexBuffer, renderType));
            }
        }
        builders.clear();

        // Translucent geometry is drawn in a later phase, after all opaque geometry.
        meshes.sort(Comparator.comparing(mesh -> mesh.renderType().sortOnUpload()));

        return meshes;
    }

    @Override
    public void close() {
        buffers.values().forEach(ByteBufferBuilder::close);
        buffers.clear();
        builders.clear();
    }
}
