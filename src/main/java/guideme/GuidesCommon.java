package guideme;

import guideme.internal.GuideMEProxy;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Functionality for guides that can be used on both the server and client.
 */
public final class GuidesCommon {
    private GuidesCommon() {
    }

    /**
     * Opens the last opened page (or start page) of the guide for the player.
     */
    public static void openGuide(Player player, ResourceLocation guideId) {
        GuideMEProxy.instance().openGuide(player, guideId, null);
    }

    /**
     * Opens the given guide for the player and navigates to the given page position.
     */
    public static void openGuide(Player player, ResourceLocation guideId, PageAnchor anchor) {
        GuideMEProxy.instance().openGuide(player, guideId, Objects.requireNonNull(anchor, "anchor"));
    }
}
