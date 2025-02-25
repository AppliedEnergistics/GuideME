package guideme.internal.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public class RequestManager {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Logger LOG = LoggerFactory.getLogger(RequestManager.class);
    private static final RequestManager instance = new RequestManager();
    private final Map<UUID, PendingRequest<?>> pendingRequests = new HashMap<>();

    public static RequestManager getInstance() {
        return instance;
    }

    private RequestManager() {
    }

    public void processTimeouts() {
        Instant now = Instant.now();

        var it = pendingRequests.values().iterator();
        while (it.hasNext()) {
            var request = it.next();
            if (request.deadline.isBefore(now)) {
                LOG.error("Request {} for {} timed out.", request.id, request.expectedPayloadType);
                it.remove();
                request.handler.accept(null, new TimeoutException());
            }
        }
    }

    public CompletableFuture<RecipeForReply> requestRecipeFor(ItemStack resultItem) {
        var request = new RecipeForRequest(
                UUID.randomUUID(),
                DEFAULT_TIMEOUT.toMillis(),
                resultItem
        );
        return sendRequest(request, request.requestId(), RecipeForReply.class);
    }

    private <T> CompletableFuture<T> sendRequest(CustomPacketPayload requestPayload, UUID id, Class<T> expectedReplyType) {
        if (pendingRequests.containsKey(id)) {
            throw new IllegalStateException("Duplicate request id: " + id);
        }
        var future = new CompletableFuture<T>();
        var request = new PendingRequest<T>(
                id,
                Instant.now().plus(DEFAULT_TIMEOUT),
                expectedReplyType,
                (result, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(result);
                    }
                }
        );
        pendingRequests.put(id, request);
        PacketDistributor.sendToServer(requestPayload);
        return future;
    }

    public void handleRecipeForRequest(ServerPlayer player, RecipeForRequest payload) {
        var recipeManager = player.server.getRecipeManager();

        var displays = new ArrayList<RecipeDisplay>();

        var context = SlotDisplayContext.fromLevel(player.level());

        // We try to find non-special recipes first then fall back to special
        for (var recipe : recipeManager.getRecipes()) {
            for (var recipeDisplay : recipe.value().display()) {
                var itemStack = recipeDisplay.result().resolveForFirstStack(context);
                if (ItemStack.isSameItemSameComponents(itemStack, payload.resultItem())) {
                    displays.add(recipeDisplay);
                    break;
                }
            }
        }

        PacketDistributor.sendToPlayer(player, new RecipeForReply(payload.requestId(), displays));
    }

    public void handleRecipeForReply(RecipeForReply payload) {
        handleReply(payload.requestId(), payload);
    }

    private void handleReply(UUID requestId, Object reply) {
        var pendingRequest = pendingRequests.remove(requestId);
        if (pendingRequest != null) {
            handleReply(pendingRequest, reply);
        } else {
            LOG.error("Received reply from server for unsolicited request {}", requestId);
        }
    }

    private <T> void handleReply(PendingRequest<T> request, Object reply) {
        T payload;
        try {
            payload = request.expectedPayloadType.cast(reply);
        } catch (ClassCastException e) {
            LOG.error("Received invalid reply of type {} for request {}, instead of expected type {}",
                    request.id, reply.getClass(), request.expectedPayloadType);
            request.handler.accept(null, e);
            return;
        }

        request.handler.accept(payload, null);
    }

    record PendingRequest<T>(UUID id,
                             Instant deadline,
                             Class<T> expectedPayloadType,
                             BiConsumer<@Nullable T, @Nullable Throwable> handler) {
    }
}
