package guideme.internal.network;

import guideme.internal.GuideME;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.List;
import java.util.UUID;

public record RecipeForReply(UUID requestId, List<RecipeDisplay> displays) implements CustomPacketPayload {
    public static Type<RecipeForReply> TYPE = new Type<>(GuideME.makeId("recipe_for_reply"));

    public static StreamCodec<RegistryFriendlyByteBuf, RecipeForReply> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RecipeForReply::requestId,
            RecipeDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeForReply::displays,
            RecipeForReply::new
    );

    @Override
    public Type<RecipeForReply> type() {
        return TYPE;
    }
}
