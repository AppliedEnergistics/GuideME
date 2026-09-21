package guideme.internal.scene;

import com.mojang.authlib.GameProfile;
import guideme.internal.util.Platform;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.ServerLinks;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;

public class FakeRenderEnvironment implements AutoCloseable {
    /**
     * The fake player and the throwaway level and connection behind it are reused across calls. Building them runs
     * two registration paths that mods hook: constructing the ClientPacketListener bootstraps PotionBrewing, which
     * fires RegisterBrewingRecipesEvent, and constructing the ClientLevel fires LevelEvent.Load. Since create() runs
     * once per scene per frame, building these fresh made every mod listening to those two events redo its
     * registration hundreds of times a second.
     */
    @Nullable
    private static LocalPlayer fakePlayer;

    /**
     * The registries the cached fake player was built against. The fake player embeds them, so it has to be rebuilt
     * whenever the client switches to a world that has different ones.
     */
    @Nullable
    private static RegistryAccess fakePlayerRegistries;

    private final @Nullable LocalPlayer originalPlayer;

    private FakeRenderEnvironment(@Nullable LocalPlayer originalPlayer) {
        this.originalPlayer = originalPlayer;
    }

    public static FakeRenderEnvironment create(Level level) {
        Minecraft minecraft = Minecraft.getInstance();

        var camera = new Camera();
        minecraft.getEntityRenderDispatcher().prepare(camera, null);

        var registries = Platform.getClientRegistryAccess();
        if (fakePlayer == null || fakePlayerRegistries != registries) {
            fakePlayer = createFakePlayer(minecraft, registries);
            fakePlayerRegistries = registries;
        }

        var originalPlayer = minecraft.player;
        minecraft.player = fakePlayer;

        return new FakeRenderEnvironment(originalPlayer);
    }

    /**
     * Drops the cached fake player so that leaving a world does not keep that world's registries alive.
     */
    public static void clearCache() {
        fakePlayer = null;
        fakePlayerRegistries = null;
    }

    private static LocalPlayer createFakePlayer(Minecraft minecraft, RegistryAccess registries) {
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        var packetListener = new ClientPacketListener(minecraft, connection, new CommonListenerCookie(
                new LevelLoadTracker(),
                new GameProfile(UUID.randomUUID(), "Site Exporter"),
                new WorldSessionTelemetryManager((eventType, propertyAdder) -> {
                }, false, null, null),
                registries.freeze(),
                FeatureFlags.VANILLA_SET,
                null,
                null,
                null,
                Map.of(),
                null,
                Map.of(),
                new ServerLinks(List.of()),
                Map.of(),
                false,
                ConnectionType.NEOFORGE));
        var levelData = new ClientLevel.ClientLevelData(
                Difficulty.NORMAL,
                false,
                false);
        var overworldType = registries
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(Level.OVERWORLD.identifier())
                .orElseThrow();
        return new LocalPlayer(
                minecraft,
                new ClientLevel(packetListener, levelData, Level.OVERWORLD, overworldType, 100, 100, null, false, 0L,
                        0),
                packetListener,
                new StatsCounter(),
                new ClientRecipeBook(),
                Input.EMPTY,
                false,
                minecraft.computeChatAbilities());
    }

    @Override
    public void close() {
        Minecraft.getInstance().player = originalPlayer;
    }
}
