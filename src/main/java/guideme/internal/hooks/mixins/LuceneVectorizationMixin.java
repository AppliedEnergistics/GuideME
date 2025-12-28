package guideme.internal.hooks.mixins;

import org.apache.lucene.internal.vectorization.VectorizationProvider;
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
    @Inject(method = "lookup", cancellable = true, at = @At("HEAD"))
    private static void lookup(boolean testMode, CallbackInfoReturnable<VectorizationProvider> ci) {
        try {
            var clazz = Class.forName("org.apache.lucene.internal.vectorization.DefaultVectorizationProvider");
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            ci.setReturnValue((VectorizationProvider) ctor.newInstance());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
