package guideme.internal;

import guideme.Guide;
import guideme.PageAnchor;
import guideme.color.LightDarkMode;
import guideme.internal.command.GuideClientCommand;
import guideme.internal.command.StructureCommands;
import guideme.internal.data.GuideMELanguageProvider;
import guideme.internal.data.GuideMEModelProvider;
import guideme.internal.hotkey.OpenGuideHotkey;
import guideme.internal.item.GuideItem;
import guideme.internal.item.GuideItemDispatchModelLoader;
import guideme.internal.screen.GlobalInMemoryHistory;
import guideme.internal.screen.GuideNavigation;
import guideme.internal.search.GuideSearch;
import guideme.render.GuiAssets;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideMEClient {
    private static final Logger LOG = LoggerFactory.getLogger(GuideMEClient.class);

    private static GuideMEClient INSTANCE;

    public static final ResourceLocation GUIDE_CLICK_ID = GuideME.makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    private final GuideSearch search = new GuideSearch();

    public GuideMEClient(ModContainer modContainer, IEventBus modBus) {
        INSTANCE = this;
        GuideME.PROXY = new GuideMEClientProxy();

        modContainer.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");

        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, GUIDE_CLICK_ID, GUIDE_CLICK_EVENT);
            }
        });
        modBus.addListener(this::gatherData);
        modBus.addListener(this::registerHotkeys);

        NeoForge.EVENT_BUS.addListener(this::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        modBus.addListener(this::resetSprites);

        OpenGuideHotkey.init();

        modBus.addListener((ModelEvent.RegisterAdditional e) -> {
            e.register(new ModelResourceLocation(GuideItem.BASE_MODEL_ID, ModelResourceLocation.STANDALONE_VARIANT));
        });
        modBus.addListener((ModelEvent.RegisterGeometryLoaders e) -> e.register(
                GuideItemDispatchModelLoader.ID, new GuideItemDispatchModelLoader()));

        modBus.addListener((RegisterClientReloadListenersEvent evt) -> {
            evt.registerReloadListener(new GuideReloadListener());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre evt) -> {
            search.processWork();
            processDevWatchers();
        });

        GuideOnStartup.init(modBus);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void processDevWatchers() {
        for (var guide : GuideRegistry.getAll()) {
            guide.tick();
        }
    }

    public static LightDarkMode currentLightDarkMode() {
        return LightDarkMode.LIGHT_MODE;
    }

    private void resetSprites(TextureAtlasStitchedEvent event) {
        if (event.getAtlas().location().equals(GuiAssets.GUI_SPRITE_ATLAS)) {
            GuiAssets.resetSprites();
        }
    }

    private void registerHotkeys(RegisterKeyMappingsEvent e) {
        e.register(OpenGuideHotkey.getHotkey());
    }

    public static GuideMEClient instance() {
        return Objects.requireNonNull(INSTANCE, "Mod is not initialized");
    }

    private final ClientConfig clientConfig = new ClientConfig();

    private void registerClientCommands(RegisterClientCommandsEvent evt) {
        var dispatcher = evt.getDispatcher();
        GuideClientCommand.register(dispatcher);
    }

    // These are meant for command blocks only usable in single player
    private void registerCommands(RegisterCommandsEvent event) {
        StructureCommands.register(event.getDispatcher());
    }

    private void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput packOutput = gen.getPackOutput();
        gen.addProvider(event.includeClient(), new GuideMELanguageProvider(packOutput));
        gen.addProvider(event.includeClient(), new GuideMEModelProvider(packOutput, event.getExistingFileHelper()));
    }

    public boolean isShowDebugGuiOverlays() {
        return clientConfig.showDebugGuiOverlays.getAsBoolean();
    }

    public boolean isAdaptiveScalingEnabled() {
        return clientConfig.adaptiveScaling.getAsBoolean();
    }

    public boolean isIgnoreTranslatedGuides() {
        return clientConfig.ignoreTranslatedGuides.getAsBoolean();
    }

    public boolean isFullWidthLayout() {
        return clientConfig.fullWidthLayout.getAsBoolean();
    }

    public void setFullWidthLayout(boolean fullWidth) {
        if (fullWidth != isFullWidthLayout()) {
            clientConfig.fullWidthLayout.set(fullWidth);
            clientConfig.spec.save();
            var minecraft = Minecraft.getInstance();
            var screen = minecraft.screen;
            if (screen != null) {
                var window = minecraft.getWindow();
                screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            }
        }
    }

    public static boolean openGuideAtPreviousPage(Guide guide, ResourceLocation initialPage) {
        try {
            var history = GlobalInMemoryHistory.get(guide);
            var historyPage = history.current();
            if (historyPage.isPresent()) {
                GuideNavigation.navigateTo(guide, historyPage.get());
            } else {
                GuideNavigation.navigateTo(guide, PageAnchor.page(initialPage));
            }
            return true;
        } catch (Exception e) {
            LOG.error("Failed to open guide.", e);
            return false;
        }
    }

    public static boolean openGuideAtAnchor(Guide guide, PageAnchor anchor) {
        try {
            GuideNavigation.navigateTo(guide, anchor);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to open guide at {}.", anchor, e);
            return false;
        }
    }

    public GuideSearch getSearch() {
        return search;
    }

    private static class ClientConfig {
        final ModConfigSpec spec;
        final ModConfigSpec.BooleanValue adaptiveScaling;
        final ModConfigSpec.BooleanValue showDebugGuiOverlays;
        final ModConfigSpec.BooleanValue fullWidthLayout;
        final ModConfigSpec.BooleanValue ignoreTranslatedGuides;

        public ClientConfig() {
            var builder = new ModConfigSpec.Builder();

            builder.push("guides");
            ignoreTranslatedGuides = builder
                    .comment("Never load translated guide pages for your current language.")
                    .define("ignoreTranslatedGuides", false);
            builder.pop();

            builder.push("gui");
            adaptiveScaling = builder
                    .comment(
                            "Adapt GUI scaling for the Guide screen to fix Minecraft font issues at GUI scale 1 and 3.")
                    .define("adaptiveScaling", true);
            fullWidthLayout = builder
                    .comment(
                            "Use the full width of the screen for the guide when it is opened.")
                    .define("fullWidthLayout", true);
            builder.pop();

            builder.push("debug");
            showDebugGuiOverlays = builder
                    .comment("Show debugging overlays in GUI on mouse-over.")
                    .define("showDebugGuiOverlays", false);
            builder.pop();

            spec = builder.build();
        }
    }
}
