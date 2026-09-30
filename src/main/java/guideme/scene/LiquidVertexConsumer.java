package guideme.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The only purpose of this vertex consumer proxy is to transform vertex positions emitted by the
 * {@link net.minecraft.client.renderer.block.FluidRenderer} using a pose. The renderer assumes it is being called in the
 * context of tessellating a chunk section (16x16x16) and emits coordinates relative to the section origin, while we
 * render all visible chunks in the guidebook together. The pose is expected to translate to the section origin.
 */
public class LiquidVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final PoseStack.Pose pose;

    public LiquidVertexConsumer(VertexConsumer delegate, PoseStack.Pose pose) {
        this.delegate = delegate;
        this.pose = pose;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return delegate.addVertex(pose, x, y, z);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return delegate.setColor(r, g, b, a);
    }

    @Override
    public VertexConsumer setColor(int color) {
        return delegate.setColor(color);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return delegate.setNormal(pose, x, y, z);
    }

    @Override
    public VertexConsumer setLight(int packedLightCoords) {
        return delegate.setLight(packedLightCoords);
    }

    @Override
    public VertexConsumer setOverlay(int packedOverlayCoords) {
        return delegate.setOverlay(packedOverlayCoords);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return delegate.setLineWidth(width);
    }
}
