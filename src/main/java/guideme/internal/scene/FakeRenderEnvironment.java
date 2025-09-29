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
    private final LocalPlayer originalPlayer;

    private FakeRenderEnvironment(@Nullable LocalPlayer originalPlayer) {
        this.originalPlayer = originalPlayer;
    }

    public static FakeRenderEnvironment create(Level level) {
        Minecraft minecraft = Minecraft.getInstance();

        var camera = new Camera();
        minecraft.getEntityRenderDispatcher().prepare(camera, null);
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        var packetListener = new ClientPacketListener(minecraft, connection, new CommonListenerCookie(
                new LevelLoadTracker(),
                new GameProfile(UUID.randomUUID(), "Site Exporter"),
                new WorldSessionTelemetryManager((eventType, propertyAdder) -> {
                }, false, null, null),
                Platform.getClientRegistryAccess().freeze(),
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
        var overworldType = Platform.getClientRegistryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(Level.OVERWORLD.location())
                .orElseThrow();
        var originalPlayer = minecraft.player;
        minecraft.player = new LocalPlayer(
                minecraft,
                new ClientLevel(packetListener, levelData, Level.OVERWORLD, overworldType, 100, 100, null, false, 0L,
                        0),
                packetListener,
                new StatsCounter(),
                new ClientRecipeBook(),
                Input.EMPTY,
                false);

        return new FakeRenderEnvironment(originalPlayer);
    }

    @Override
    public void close() {
        Minecraft.getInstance().player = originalPlayer;
    }
}
