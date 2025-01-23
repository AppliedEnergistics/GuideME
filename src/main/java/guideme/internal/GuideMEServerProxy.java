package guideme.internal;

import guideme.PageAnchor;
import guideme.internal.network.OpenGuideRequest;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

class GuideMEServerProxy implements GuideMEProxy {
    @Override
    public boolean openGuide(Player player, ResourceLocation id) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenGuideRequest(id));
            return true;
        }

        return false;
    }

    @Override
    public boolean openGuide(Player player, ResourceLocation guideId, @Nullable PageAnchor anchor) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenGuideRequest(guideId, Optional.ofNullable(anchor)));
            return true;
        }

        return false;
    }

    @Override
    public List<GuideMetadata> getAvailableGuides() {
        return List.of();
    }
}
