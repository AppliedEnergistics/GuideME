package guideme.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.SectionPos;

/**
 * The only purpose of this vertex consumer proxy is to transform vertex positions emitted by the
 * {@link net.minecraft.client.renderer.block.FluidRenderer} into absolute coordinates. The renderer assumes it is
 * being called in the context of tessellating a chunk section (16x16x16) and emits corresponding coordinates, while we
 * batch all visible chunks in the guidebook together.
 */
public class LiquidVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final SectionPos sectionPos;

    public LiquidVertexConsumer(VertexConsumer delegate, SectionPos sectionPos) {
        this.delegate = delegate;
        this.sectionPos = sectionPos;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        x += sectionPos.getX() * SectionPos.SECTION_SIZE;
        y += sectionPos.getY() * SectionPos.SECTION_SIZE;
        z += sectionPos.getZ() * SectionPos.SECTION_SIZE;

        return delegate.addVertex(x, y, z);
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
        return delegate.setNormal(x, y, z);
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
