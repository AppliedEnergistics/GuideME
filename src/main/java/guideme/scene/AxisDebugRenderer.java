package guideme.scene;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import guideme.internal.scene.SceneRenderTarget;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Renders a 3d cross to visualize the alignment of the x, y, and z axes.
 */
final public class AxisDebugRenderer implements AutoCloseable {

    private final GpuBuffer crosshairBuffer;
    private final RenderSystem.AutoStorageIndexBuffer crosshairIndicies = RenderSystem
            .getSequentialBuffer(PrimitiveTopology.LINES);
    private final ProjectionMatrixBuffer projectionBuffer;

    public AxisDebugRenderer() {
        try (ByteBufferBuilder bytebufferbuilder = ByteBufferBuilder
                .exactlySized(DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize() * 12)) {
            BufferBuilder bufferbuilder = new BufferBuilder(bytebufferbuilder, PrimitiveTopology.LINES,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL);
            bufferbuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(0XFFFF0000).setNormal(1.0F, 0.0F, 0.0F);
            bufferbuilder.addVertex(25, 0.0F, 0.0F).setColor(0XFFFF0000).setNormal(1.0F, 0.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(0XFF00FF00).setNormal(0.0F, 1.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 25, 0.0F).setColor(0XFF00FF00).setNormal(0.0F, 1.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(0XFF7F7FFF).setNormal(0.0F, 0.0F, 1.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, 25).setColor(0XFF7F7FFF).setNormal(0.0F, 0.0F, 1.0F);

            try (MeshData meshdata = bufferbuilder.buildOrThrow()) {
                this.crosshairBuffer = RenderSystem.getDevice().createBuffer(() -> "GuideME crosshair vertex buffer",
                        GpuBuffer.USAGE_VERTEX, meshdata.vertexBuffer());
            }
        }

        projectionBuffer = new ProjectionMatrixBuffer("debug crosshair projection");
    }

    public void render(CameraSettings cameraSettings) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(cameraSettings.getProjectionMatrix()),
                ProjectionType.ORTHOGRAPHIC);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(cameraSettings.getViewMatrix());

        RenderPipeline renderpipeline = RenderPipelines.LINES;
        GpuBuffer gpubuffer = this.crosshairIndicies.getBuffer(18);
        GpuBufferSlice dynamicTransform = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack));

        try (RenderPass renderpass = SceneRenderTarget.createRenderPass(() -> "3d crosshair")) {
            renderpass.setPipeline(RenderSystem.getCompiledPipeline(renderpipeline));
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setVertexBuffer(0, this.crosshairBuffer.slice());
            renderpass.setIndexBuffer(gpubuffer, this.crosshairIndicies.type());
            renderpass.setUniform("DynamicTransforms", dynamicTransform);
            renderpass.drawIndexed(18, 1, 0, 0, 0);
        }

        modelViewStack.popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    @Override
    public void close() {
        crosshairBuffer.close();
        projectionBuffer.close();
    }
}
