package guideme.internal;

import guideme.internal.command.GuideCommand;
import guideme.internal.command.GuideIdArgument;
import guideme.internal.command.PageAnchorArgument;
import guideme.internal.item.GuideItem;
import guideme.internal.network.OpenGuideRequest;
import java.util.function.Supplier;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = GuideME.MOD_ID)
public class GuideME {
    static GuideMEProxy PROXY = new GuideMEServerProxy();

    public static final String MOD_ID = "guideme";

    private static final DeferredRegister.Items DR_ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> DR_ARGUMENT_TYPE_INFOS = DeferredRegister
            .create(Registries.COMMAND_ARGUMENT_TYPE, MOD_ID);

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
        DR_ARGUMENT_TYPE_INFOS.register("guide_id", () -> ArgumentTypeInfos.registerByClass(GuideIdArgument.class,
                SingletonArgumentInfo.contextFree(GuideIdArgument::argument)));
        DR_ARGUMENT_TYPE_INFOS.register("page_anchor", () -> ArgumentTypeInfos.registerByClass(PageAnchorArgument.class,
                SingletonArgumentInfo.contextFree(PageAnchorArgument::argument)));

        var drDataComponents = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
        drDataComponents.register("guide_id", () -> GUIDE_ID_COMPONENT);

        DR_ARGUMENT_TYPE_INFOS.register(modBus);
        DR_ITEMS.register(modBus);
        drDataComponents.register(modBus);

        modBus.addListener(this::registerNetworking);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        GuideCommand.register(event.getDispatcher());
    }

    private void registerNetworking(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0");
        registrar.playToClient(OpenGuideRequest.TYPE, OpenGuideRequest.STREAM_CODEC, (payload, context) -> {
            var anchor = payload.pageAnchor().orElse(null);
            if (anchor != null) {
                GuideMEProxy.instance().openGuide(context.player(), payload.guideId(), anchor);
            } else {
                GuideMEProxy.instance().openGuide(context.player(), payload.guideId());
            }
        });
    }

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
