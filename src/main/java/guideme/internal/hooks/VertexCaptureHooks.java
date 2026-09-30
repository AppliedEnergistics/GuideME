package guideme.internal.hooks;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Allows redirecting the vertices emitted by feature renderers into our own buffers, so they can be captured instead of
 * being drawn. This is used to export game scenes as 3D meshes.
 *
 * @see guideme.internal.hooks.mixins.RenderTypeFeatureRendererMixin
 */
@ApiStatus.Internal
public final class VertexCaptureHooks {
    @Nullable
    private static Function<RenderType, VertexConsumer> capture;

    private VertexCaptureHooks() {
    }

    /**
     * @return The buffer to write vertices for the given render type into, or null if no capture is active.
     */
    @Nullable
    public static VertexConsumer getCaptureBuffer(RenderType renderType) {
        var currentCapture = capture;
        return currentCapture != null ? currentCapture.apply(renderType) : null;
    }

    /**
     * Captures all vertices emitted by feature renderers while running the given code.
     */
    public static void captureDuring(Function<RenderType, VertexConsumer> bufferGetter, Runnable runnable) {
        RenderSystem.assertOnRenderThread();
        if (capture != null) {
            throw new IllegalStateException("A vertex capture is already active");
        }
        capture = bufferGetter;
        try {
            runnable.run();
        } finally {
            capture = null;
        }
    }
}
