package guideme.internal;

import guideme.PageAnchor;
import guideme.internal.network.OpenGuideRequest;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

class GuideMEServerProxy implements GuideMEProxy {
    @Override
    public boolean openGuide(Player player, Identifier id) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenGuideRequest(id));
            return true;
        }

        return false;
    }

    @Override
    public boolean openGuide(Player player, Identifier guideId, @Nullable PageAnchor anchor) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenGuideRequest(guideId, Optional.ofNullable(anchor)));
            return true;
        }

        return false;
    }

    @Override
    public Stream<Identifier> getAvailableGuides() {
        return Stream.empty();
    }

    @Override
    public Stream<Identifier> getAvailablePages(Identifier guideId) {
        return Stream.empty();
    }
}
