package guideme.internal.hooks.mixins;

import com.mojang.blaze3d.vertex.VertexConsumer;
import guideme.internal.hooks.VertexCaptureHooks;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nearly all feature renderers obtain their vertex buffers through this method, which makes it the only place where the
 * emitted geometry can be associated with its render type. We use this to capture game scenes for export.
 */
@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
    @Inject(method = "getVertexBuilder", at = @At("HEAD"), cancellable = true)
    private void guideme$captureVertices(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        var captureBuffer = VertexCaptureHooks.getCaptureBuffer(renderType);
        if (captureBuffer != null) {
            cir.setReturnValue(captureBuffer);
        }
    }
}
