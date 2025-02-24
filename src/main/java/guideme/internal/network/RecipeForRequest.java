package guideme.internal.network;

import guideme.internal.GuideME;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record RecipeForRequest(UUID requestId, long deadlineInMs, ItemStack resultItem) implements CustomPacketPayload {

    public static Type<RecipeForRequest> TYPE = new CustomPacketPayload.Type<>(GuideME.makeId("recipe_for_request"));

    public static StreamCodec<RegistryFriendlyByteBuf, RecipeForRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RecipeForRequest::requestId,
            ByteBufCodecs.LONG, RecipeForRequest::deadlineInMs,
            ItemStack.STREAM_CODEC, RecipeForRequest::resultItem,
            RecipeForRequest::new);

    @Override
    public Type<RecipeForRequest> type() {
        return TYPE;
    }
}
