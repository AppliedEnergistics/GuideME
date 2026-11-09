package guideme.testmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TestMod.MOD_ID)
public class TestMod {
    public static final String MOD_ID = "testmod";

    private static final DeferredRegister<RecipeSerializer<?>> DR_RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, MOD_ID);

    static {
        DR_RECIPE_SERIALIZERS.register("stick_bucket", () -> StickBucketRecipe.SERIALIZER);
    }

    public TestMod(IEventBus modBus) {
        DR_RECIPE_SERIALIZERS.register(modBus);
    }
}
