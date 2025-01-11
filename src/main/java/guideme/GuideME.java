package guideme;

import guideme.command.GuideIdArgument;
import guideme.data.GuideMELanguageProvider;
import guideme.guidebook.Guide;
import java.util.List;
import java.util.Objects;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideME {

    public static final String MOD_ID = "guideme";

    private static GuideME INSTANCE;

    public static final ResourceLocation GUIDE_CLICK_ID = makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    public GuideME(ModContainer modContainer, IEventBus modBus) {
        INSTANCE = this;

        modContainer.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");

        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, GUIDE_CLICK_ID, GUIDE_CLICK_EVENT);
            }
        });
        modBus.addListener(this::gatherData);

        NeoForge.EVENT_BUS.addListener(GuideME::registerClientCommands);
    }

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static GuideME instance() {
        return Objects.requireNonNull(INSTANCE, "Mod is not initialized");
    }

    private final ClientConfig clientConfig = new ClientConfig();

    private static void registerClientCommands(RegisterClientCommandsEvent evt) {
        var dispatcher = evt.getDispatcher();

        dispatcher.register(Commands.literal("guideme").then(
                Commands.literal("export_site_data")
                        .then(Commands.argument("guide", GuideIdArgument.argument()))
                        .executes(context -> {
                            // Do we have a registered site exporter for the guide?
                            // TODO
                            return 0;
                        })));

    }

    private void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput packOutput = gen.getPackOutput();
        gen.addProvider(event.includeClient(), new GuideMELanguageProvider(packOutput));
    }

    public static List<ResourceLocation> getGuides() {
        return List.of(); // TODO
    }

    public static Guide getGuideById(ResourceLocation id) {
        // TODO
        return null;
    }

    public boolean isShowDebugGuiOverlays() {
        return clientConfig.showDebugGuiOverlays.getAsBoolean();
    }

    private static class ClientConfig {
        final ModConfigSpec spec;
        final ModConfigSpec.BooleanValue showDebugGuiOverlays;

        public ClientConfig() {
            var builder = new ModConfigSpec.Builder();

            builder.push("debug");
            showDebugGuiOverlays = builder.define("showDebugGuiOverlays", false);
            builder.pop();

            spec = builder.build();
        }
    }

}
