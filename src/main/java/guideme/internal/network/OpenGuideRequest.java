package guideme.internal.network;

import guideme.PageAnchor;
import guideme.internal.GuideME;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGuideRequest(ResourceLocation guideId,
        Optional<PageAnchor> pageAnchor) implements CustomPacketPayload {

    public static final ResourceLocation ID = GuideME.makeId("open_guide");

    public OpenGuideRequest(ResourceLocation guideId) {
        this(guideId, Optional.empty());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(guideId);
        buffer.writeOptional(pageAnchor, PageAnchor::write);
    }

    public static OpenGuideRequest read(FriendlyByteBuf buffer) {
        return new OpenGuideRequest(
                buffer.readResourceLocation(),
                buffer.readOptional(PageAnchor::read));
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
