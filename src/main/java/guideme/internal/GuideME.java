package guideme.internal;

import guideme.internal.command.GuideCommand;
import guideme.internal.command.GuideIdArgument;
import guideme.internal.command.PageAnchorArgument;
import guideme.internal.item.GuideItem;
import guideme.internal.network.OpenGuideRequest;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = GuideME.MOD_ID)
public class GuideME {
    static GuideMEProxy PROXY = new GuideMEServerProxy();

    public static final String MOD_ID = "guideme";

    private static final DeferredRegister<Item> DR_ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> DR_ARGUMENT_TYPE_INFOS = DeferredRegister
            .create(Registries.COMMAND_ARGUMENT_TYPE, MOD_ID);

    public static final Supplier<GuideItem> GUIDE_ITEM = DR_ITEMS.register("guide",
            () -> new GuideItem(GuideItem.PROPERTIES));

    private static GuideME INSTANCE;

    public GuideME(IEventBus modBus) {
        ModLoadingContext context = ModLoadingContext.get();

        INSTANCE = this;

        DR_ARGUMENT_TYPE_INFOS.register("guide_id", () -> ArgumentTypeInfos.registerByClass(GuideIdArgument.class,
                SingletonArgumentInfo.contextFree(GuideIdArgument::argument)));
        DR_ARGUMENT_TYPE_INFOS.register("page_anchor", () -> ArgumentTypeInfos.registerByClass(PageAnchorArgument.class,
                SingletonArgumentInfo.contextFree(PageAnchorArgument::argument)));

        DR_ARGUMENT_TYPE_INFOS.register(modBus);
        DR_ITEMS.register(modBus);

        modBus.addListener(this::registerNetworking);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        if (FMLLoader.getDist().isClient()) {
            new GuideMEClient(context, modBus);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        GuideCommand.register(event.getDispatcher());
    }

    private void registerNetworking(RegisterPayloadHandlerEvent event) {
        var registrar = event.registrar(GuideME.MOD_ID).versioned("1.0");
        registrar.play(OpenGuideRequest.ID, OpenGuideRequest::read, (payload, context) -> {
            var player = context.player().orElse(null);
            if (player == null) {
                return;
            }

            context.workHandler().execute(() -> {
                var anchor = payload.pageAnchor().orElse(null);
                if (anchor != null) {
                    GuideMEProxy.instance().openGuide(player, payload.guideId(), anchor);
                } else {
                    GuideMEProxy.instance().openGuide(player, payload.guideId());
                }
            });
        });
    }

    public static ResourceLocation makeId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static GuideME instance() {
        return Objects.requireNonNull(INSTANCE, "instance");
    }
}
