package guideme;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Objects;

@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideME {

    public static final String MOD_ID = "guideme";

    private static GuideME INSTANCE;

    public static final ResourceLocation GUIDE_CLICK_ID = makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static GuideME instance() {
        return Objects.requireNonNull(INSTANCE, "Mod is not initialized");
    }

    private final ClientConfig clientConfig = new ClientConfig();

    public boolean isShowDebugGuiOverlays() {
        return clientConfig.showDebugGuiOverlays.getAsBoolean();
    }

    public GuideME(ModContainer modContainer, IEventBus modBus) {
        INSTANCE = this;

        modContainer.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");

        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, GUIDE_CLICK_ID, GUIDE_CLICK_EVENT);
            }
        });
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
