package guideme.guidebook.document.block;

import guideme.guidebook.document.LytRect;
import guideme.guidebook.document.interaction.GuideTooltip;
import guideme.guidebook.document.interaction.InteractiveElement;
import guideme.guidebook.document.interaction.ItemTooltip;
import guideme.guidebook.layout.LayoutContext;
import guideme.guidebook.render.GuiAssets;
import guideme.guidebook.render.GuiSprite;
import guideme.guidebook.render.RenderContext;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Renders a standard Minecraft GUI slot.
 */
public class LytSlot extends LytBlock implements InteractiveElement {

    private static final int ITEM_SIZE = 16;
    private static final int PADDING = 1;
    private static final int LARGE_PADDING = 5;
    public static final int OUTER_SIZE = ITEM_SIZE + 2 * PADDING;
    public static final int OUTER_SIZE_LARGE = ITEM_SIZE + 2 * LARGE_PADDING;
    private static final int CYCLE_TIME = 2000;

    private boolean largeSlot;

    private final ItemStack[] stacks;

    public LytSlot(Ingredient ingredient) {
        this.stacks = ingredient.getItems();
    }

    public LytSlot(ItemStack stack) {
        this.stacks = new ItemStack[] { stack };
    }

    public boolean isLargeSlot() {
        return largeSlot;
    }

    public void setLargeSlot(boolean largeSlot) {
        this.largeSlot = largeSlot;
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        if (largeSlot) {
            return new LytRect(x, y, OUTER_SIZE_LARGE, OUTER_SIZE_LARGE);
        } else {
            return new LytRect(x, y, OUTER_SIZE, OUTER_SIZE);
        }
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {

    }

    @Override
    public void render(RenderContext context) {
        var x = bounds.x();
        var y = bounds.y();

        GuiSprite texture;
        if (largeSlot) {
            texture = GuiAssets.LARGE_SLOT;
        } else {
            texture = GuiAssets.SLOT;
        }
        context.fillIcon(bounds, texture);

        var padding = largeSlot ? LARGE_PADDING : PADDING;

        var stack = getDisplayedStack();
        if (!stack.isEmpty()) {
            context.renderItem(stack, x + padding, y + padding, 1, ITEM_SIZE, ITEM_SIZE);
        }
    }

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        var stack = getDisplayedStack();
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ItemTooltip(stack));
    }

    private ItemStack getDisplayedStack() {
        if (stacks.length == 0) {
            return ItemStack.EMPTY;
        }

        var cycle = System.nanoTime() / TimeUnit.MILLISECONDS.toNanos(CYCLE_TIME);
        return stacks[(int) (cycle % stacks.length)];
    }
}
