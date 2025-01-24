package guideme.internal.network;

import guideme.PageAnchor;
import guideme.internal.GuideME;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGuideRequest(ResourceLocation guideId,
        Optional<PageAnchor> pageAnchor) implements CustomPacketPayload {

    public OpenGuideRequest(ResourceLocation guideId) {
        this(guideId, Optional.empty());
    }

    public static final Type<OpenGuideRequest> TYPE = new Type<>(GuideME.makeId("open_guide"));

    public static final StreamCodec<FriendlyByteBuf, PageAnchor> ANCHOR_STREAM_CODEC = StreamCodec
            .of((buffer, value) -> {
                buffer.writeResourceLocation(value.pageId());
                buffer.writeNullable(value.anchor(), ByteBufCodecs.STRING_UTF8);
            }, buffer -> {
                var page = buffer.readResourceLocation();
                var fragment = buffer.readNullable(ByteBufCodecs.STRING_UTF8);
                return new PageAnchor(page, fragment);
            });

    public static final StreamCodec<FriendlyByteBuf, OpenGuideRequest> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, OpenGuideRequest::guideId,
            ByteBufCodecs.optional(ANCHOR_STREAM_CODEC), OpenGuideRequest::pageAnchor,
            OpenGuideRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
