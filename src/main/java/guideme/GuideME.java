package guideme;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Objects;

@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideME {

    public static final String MOD_ID = "guideme";

    private static GuideME INSTANCE;

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

    public GuideME(ModContainer modContainer, IModBusEvent modBus) {
        INSTANCE = this;

        modContainer.registerConfig(ModConfig.Type.CLIENT, clientConfig.spec, "guideme.toml");
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
