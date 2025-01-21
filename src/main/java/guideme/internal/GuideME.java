package guideme.internal;

import guideme.internal.item.GuideItem;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = GuideME.MOD_ID)
public class GuideME {
    public static GuideMEProxy PROXY = new GuideMEProxy() {
    };

    public static final String MOD_ID = "guideme";

    private static final DeferredRegister.Items DR_ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final Supplier<GuideItem> GUIDE_ITEM = DR_ITEMS.registerItem("guide", GuideItem::new,
            GuideItem.PROPERTIES);

    /**
     * Attaches the guide ID to a generic guide item.
     */
    public static final DataComponentType<ResourceLocation> GUIDE_ID_COMPONENT = DataComponentType
            .<ResourceLocation>builder()
            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            .persistent(ResourceLocation.CODEC)
            .build();

    public GuideME(IEventBus modBus) {
        var drDataComponents = DeferredRegister.createDataComponents(MOD_ID);
        drDataComponents.register("guide_id", () -> GUIDE_ID_COMPONENT);

        DR_ITEMS.register(modBus);
        drDataComponents.register(modBus);
    }

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
