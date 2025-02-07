package guideme.mixins;

import guideme.internal.GuideME;
import guideme.render.GuiAssets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelManager.class)
public abstract class AtlasMixin {
    @Shadow
    @Final
    @Mutable
    private static Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES;

    @Inject(at = @At("TAIL"), method = "<clinit>")
    private static void addGuideMeAtlas(CallbackInfo ci) {
        var atlases = new HashMap<>(VANILLA_ATLASES);
        atlases.put(GuiAssets.GUI_SPRITE_ATLAS, GuideME.makeId("gui"));
        VANILLA_ATLASES = Map.copyOf(atlases);
    }
}
