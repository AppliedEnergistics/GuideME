package guideme.internal.network;

import guideme.PageAnchor;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record OpenGuideRequest(ResourceLocation guideId, Optional<PageAnchor> pageAnchor) {
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
}
