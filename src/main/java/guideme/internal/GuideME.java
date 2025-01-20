package guideme.internal;

import guideme.Guide;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.color.LightDarkMode;
import guideme.internal.command.GuideCommand;
import guideme.internal.data.GuideMELanguageProvider;
import guideme.internal.data.GuideMEModelProvider;
import guideme.internal.hotkey.OpenGuideHotkey;
import guideme.internal.item.GuideItem;
import guideme.internal.item.GuideItemDispatchModelLoader;
import guideme.render.GuiAssets;
import guideme.screen.GlobalInMemoryHistory;
import guideme.screen.GuideScreen;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideME implements GuideMEProxy {
    private static final Logger LOG = LoggerFactory.getLogger(GuideME.class);

    public static final String MOD_ID = "guideme";

    private static GuideME INSTANCE;

    public static final ResourceLocation GUIDE_CLICK_ID = makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    public GuideME(ModContainer modContainer, IEventBus modBus) {
        INSTANCE = this;
        GuideMECommon.PROXY = this;

        modContainer.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");

        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, GUIDE_CLICK_ID, GUIDE_CLICK_EVENT);
            }
        });
        modBus.addListener(this::gatherData);
        modBus.addListener(this::registerHotkeys);

        NeoForge.EVENT_BUS.addListener(GuideME::registerClientCommands);
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

        GuideOnStartup.init(modBus);

        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            System.out.println();
        });
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
        gen.addProvider(event.includeClient(), new GuideMEModelProvider(packOutput, event.getExistingFileHelper()));
    }

    public boolean isShowDebugGuiOverlays() {
        return clientConfig.showDebugGuiOverlays.getAsBoolean();
    }

    public static boolean openGuideAtPreviousPage(Guide guide, ResourceLocation initialPage) {
        try {
            var screen = GuideScreen.openAtPreviousPage(guide, PageAnchor.page(initialPage),
                    GlobalInMemoryHistory.INSTANCE);

            openGuideScreen(screen);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to open guide.", e);
            return false;
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

    @Override
    public void getDataDrivenItemTooltip(ResourceLocation guideId, Item.TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
        var guide = Guides.getById(guideId);
        if (guide == null) {
            lines.add(GuidebookText.ItemInvalidGuideId.text().withStyle(ChatFormatting.RED));
            return;
        }
    }

    @Override
    public boolean openGuide(Player player, ResourceLocation id) {
        var guide = Guides.getById(id);
        if (guide == null) {
            player.sendSystemMessage(GuidebookText.ItemInvalidGuideId.text(id.toString()));
            return false;
        } else {
            return openGuideAtPreviousPage(guide, guide.getStartPage());
        }
    }
}
