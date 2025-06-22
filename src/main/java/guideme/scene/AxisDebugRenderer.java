package guideme.scene;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Renders a 3d cross to visualize the alignment of the x, y, and z axes.
 */
final public class AxisDebugRenderer implements AutoCloseable {

    private final GpuBuffer crosshairBuffer;
    private final RenderSystem.AutoStorageIndexBuffer crosshairIndicies = RenderSystem
            .getSequentialBuffer(VertexFormat.Mode.LINES);
    private final PerspectiveProjectionMatrixBuffer projectionBuffer;

    public AxisDebugRenderer() {
        try (ByteBufferBuilder bytebufferbuilder = ByteBufferBuilder
                .exactlySized(DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize() * 12)) {
            BufferBuilder bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.LINES,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL);
            bufferbuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(0XFFFF0000).setNormal(1.0F, 0.0F, 0.0F);
            bufferbuilder.addVertex(25, 0.0F, 0.0F).setColor(0XFFFF0000).setNormal(1.0F, 0.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(0XFF00FF00).setNormal(0.0F, 1.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 25, 0.0F).setColor(0XFF00FF00).setNormal(0.0F, 1.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(0XFF7F7FFF).setNormal(0.0F, 0.0F, 1.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, 25).setColor(0XFF7F7FFF).setNormal(0.0F, 0.0F, 1.0F);

            try (MeshData meshdata = bufferbuilder.buildOrThrow()) {
                this.crosshairBuffer = RenderSystem.getDevice().createBuffer(() -> "GuideME crosshair vertex buffer",
                        32, meshdata.vertexBuffer());
            }
        }

        projectionBuffer = new PerspectiveProjectionMatrixBuffer("debug crosshair projection");
    }

    public void render(CameraSettings cameraSettings) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(cameraSettings.getProjectionMatrix()),
                ProjectionType.ORTHOGRAPHIC);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(cameraSettings.getViewMatrix());

        RenderPipeline renderpipeline = RenderPipelines.LINES;
        RenderTarget rendertarget = Minecraft.getInstance().getMainRenderTarget();
        var colorView = Objects.requireNonNullElse(RenderSystem.outputColorTextureOverride,
                rendertarget.getColorTextureView());
        var depthView = Objects.requireNonNullElse(RenderSystem.outputDepthTextureOverride,
                rendertarget.getDepthTextureView());
        GpuBuffer gpubuffer = this.crosshairIndicies.getBuffer(18);
        GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms()
                .writeTransforms(
                        new DynamicUniforms.Transform(new Matrix4f(modelViewStack),
                                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 2.0F));

        try (RenderPass renderpass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "3d crosshair", colorView, OptionalInt.empty(), depthView,
                        OptionalDouble.empty())) {
            renderpass.setPipeline(renderpipeline);
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setVertexBuffer(0, this.crosshairBuffer);
            renderpass.setIndexBuffer(gpubuffer, this.crosshairIndicies.type());
            renderpass.setUniform("DynamicTransforms", slices[0]);
            renderpass.drawIndexed(0, 0, 18, 1);
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
