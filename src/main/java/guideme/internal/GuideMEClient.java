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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuideMEClient {
    private static final Logger LOG = LoggerFactory.getLogger(GuideMEClient.class);

    private static GuideMEClient INSTANCE;

    public static final ResourceLocation GUIDE_CLICK_ID = GuideME.makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    private final GuideSearch search = new GuideSearch();

    private GuiSpriteAtlas guiAtlas;

    public GuideMEClient(ModLoadingContext context, IEventBus modBus) {
        INSTANCE = this;
        GuideME.PROXY = new GuideMEClientProxy();

        context.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");

        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                e.register(Registries.SOUND_EVENT, GUIDE_CLICK_ID, () -> GUIDE_CLICK_EVENT);
            }
        });
        modBus.addListener(this::gatherData);
        modBus.addListener(this::registerHotkeys);

        MinecraftForge.EVENT_BUS.addListener(this::registerClientCommands);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        modBus.addListener(this::resetSprites);

        OpenGuideHotkey.init();

        modBus.addListener((ModelEvent.RegisterAdditional e) -> {
            e.register(GuideItem.BASE_MODEL_ID);
        });
        modBus.addListener((ModelEvent.RegisterGeometryLoaders e) -> e.register(
                GuideItemDispatchModelLoader.ID.getPath(), new GuideItemDispatchModelLoader()));

        modBus.addListener((RegisterClientReloadListenersEvent evt) -> {
            evt.registerReloadListener(new GuideReloadListener());
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent evt) -> {
            if (evt.phase == TickEvent.Phase.START) {
                search.processWork();
                processDevWatchers();
            }
        });

        modBus.addListener(this::registerReloadListener);

        GuideOnStartup.init(modBus);
    }

    private void registerReloadListener(RegisterClientReloadListenersEvent ev) {
        if (guiAtlas == null) {
            guiAtlas = new GuiSpriteAtlas(
                    Minecraft.getInstance().textureManager,
                    GuiAssets.GUI_SPRITE_ATLAS,
                    GuideME.makeId("gui"));
        }

        ev.registerReloadListener(guiAtlas);
    }

    private void processDevWatchers() {
        for (var guide : GuideRegistry.getAll()) {
            guide.tick();
        }
    }

    public static LightDarkMode currentLightDarkMode() {
        return LightDarkMode.LIGHT_MODE;
    }

    private void resetSprites(TextureStitchEvent event) {
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
        return clientConfig.showDebugGuiOverlays.get();
    }

    public boolean isAdaptiveScalingEnabled() {
        return clientConfig.adaptiveScaling.get();
    }

    public boolean isIgnoreTranslatedGuides() {
        return clientConfig.ignoreTranslatedGuides.get();
    }

    public boolean isFullWidthLayout() {
        return clientConfig.fullWidthLayout.get();
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
        final ForgeConfigSpec spec;
        final ForgeConfigSpec.BooleanValue adaptiveScaling;
        final ForgeConfigSpec.BooleanValue showDebugGuiOverlays;
        final ForgeConfigSpec.BooleanValue fullWidthLayout;
        final ForgeConfigSpec.BooleanValue ignoreTranslatedGuides;

        public ClientConfig() {
            var builder = new ForgeConfigSpec.Builder();

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

    public TextureAtlas getGuiSpriteAtlas() {
        return Objects.requireNonNull(guiAtlas).getTextureAtlas();
    }
}
