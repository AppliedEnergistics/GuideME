package guideme.internal.hooks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This mixin forces Lucene not to rely on the incubating Vector module, which currently causes it to print a startup
 * error to console, and, when someone *does* enable the incubating module, crashes due to us not shipping the
 * corresponding provider.
 */
@Mixin(targets = "guideme.internal.shaded.lucene.internal.vectorization.VectorizationProvider", remap = false)
public class LuceneVectorizationMixin {
    @Inject(method = "getUpperJavaFeatureVersion", cancellable = true, at = @At("TAIL"))
    private static void getUpperJavaFeatureVersion(CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(17);
    }
}
