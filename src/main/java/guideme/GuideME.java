package guideme;

import guideme.command.GuideCommand;
import guideme.data.GuideMELanguageProvider;
import guideme.guidebook.Guide;
import guideme.guidebook.PageAnchor;
import guideme.guidebook.hotkey.OpenGuideHotkey;
import guideme.guidebook.screen.GlobalInMemoryHistory;
import guideme.guidebook.screen.GuideScreen;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideME {
    private static final Logger LOG = LoggerFactory.getLogger(GuideME.class);

    public static final String MOD_ID = "guideme";

    private static GuideME INSTANCE;

    public static final ResourceLocation GUIDE_CLICK_ID = makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    public static final ResourceKey<Registry<Guide>> GUIDES_REGISTRY = ResourceKey.createRegistryKey(makeId("guides"));

    public static final Registry<Guide> GUIDES = new RegistryBuilder<>(GUIDES_REGISTRY)
            .sync(false)
            .create();

    public GuideME(ModContainer modContainer, IEventBus modBus) {
        INSTANCE = this;

        modContainer.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");

        modBus.addListener((NewRegistryEvent e) -> e.register(GUIDES));

        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, GUIDE_CLICK_ID, GUIDE_CLICK_EVENT);
            }
        });
        modBus.addListener(this::gatherData);
        modBus.addListener(this::registerHotkeys);

        NeoForge.EVENT_BUS.addListener(GuideME::registerClientCommands);

        OpenGuideHotkey.init();
    }

    private void registerHotkeys(RegisterKeyMappingsEvent e) {
        e.register(OpenGuideHotkey.getHotkey());
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
        GuideCommand.register(dispatcher);
    }

    private void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput packOutput = gen.getPackOutput();
        gen.addProvider(event.includeClient(), new GuideMELanguageProvider(packOutput));
    }

    public static Guide getGuideById(ResourceLocation id) {
        return GUIDES.get(id);
    }

    public boolean isShowDebugGuiOverlays() {
        return clientConfig.showDebugGuiOverlays.getAsBoolean();
    }

    public static void openGuideAtPreviousPage(Guide guide, ResourceLocation initialPage) {
        try {
            var screen = GuideScreen.openAtPreviousPage(guide, PageAnchor.page(initialPage),
                    GlobalInMemoryHistory.INSTANCE);

            openGuideScreen(screen);
        } catch (Exception e) {
            LOG.error("Failed to open guide.", e);
        }
    }

    public static void openGuideAtAnchor(Guide guide, PageAnchor anchor) {
        try {
            var screen = GuideScreen.openNew(guide, anchor, GlobalInMemoryHistory.INSTANCE);

            openGuideScreen(screen);
        } catch (Exception e) {
            LOG.error("Failed to open guide at {}.", anchor, e);
        }
    }

    private static void openGuideScreen(GuideScreen screen) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            screen.setReturnToOnClose(minecraft.screen);
        }

        minecraft.setScreen(screen);
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
