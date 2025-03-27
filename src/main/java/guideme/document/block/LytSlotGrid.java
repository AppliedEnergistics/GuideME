package guideme.document.block;

import guideme.document.LytRect;
import guideme.layout.LayoutContext;
import guideme.render.GuiAssets;
import guideme.render.RenderContext;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public class LytSlotGrid extends LytBox {
    private final int width;
    private final int height;
    private final LytSlot[] slots;
    private boolean renderEmptySlots = true;

    public LytSlotGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.slots = new LytSlot[width * height];
    }

    public static LytSlotGrid columnFromStacks(List<ItemStack> items, boolean skipEmpty) {
        return columnFromDisplays(items.stream().map(SlotDisplay.ItemStackSlotDisplay::new).toList(), skipEmpty);
    }

    public static LytSlotGrid rowFromStacks(List<ItemStack> items, boolean skipEmpty) {
        return rowFromDisplays(items.stream().map(SlotDisplay.ItemStackSlotDisplay::new).toList(), skipEmpty);
    }

    public static LytSlotGrid columnFromIngredients(List<Ingredient> ingredients, boolean skipEmpty) {
        if (!skipEmpty) {
            var grid = new LytSlotGrid(1, ingredients.size());
            for (int i = 0; i < ingredients.size(); i++) {
                grid.setIngredient(0, i, ingredients.get(i));
            }
            return grid;
        }

        var nonEmptyIngredients = (int) ingredients.stream().filter(i -> !i.isEmpty()).count();
        var grid = new LytSlotGrid(1, nonEmptyIngredients);
        var index = 0;
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                grid.setIngredient(0, index++, ingredient);
            }
        }
        return grid;
    }

    public static LytSlotGrid rowFromIngredients(List<Ingredient> ingredients, boolean skipEmpty) {
        if (!skipEmpty) {
            var grid = new LytSlotGrid(ingredients.size(), 1);
            for (int i = 0; i < ingredients.size(); i++) {
                grid.setIngredient(i, 0, ingredients.get(i));
            }
            return grid;
        }

        var nonEmptyIngredients = (int) ingredients.stream().filter(i -> !i.isEmpty()).count();
        var grid = new LytSlotGrid(nonEmptyIngredients, 1);
        var index = 0;
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                grid.setIngredient(index++, 0, ingredient);
            }
        }
        return grid;
    }

    public static LytSlotGrid columnFromDisplays(List<? extends SlotDisplay> displays, boolean skipEmpty) {
        if (!skipEmpty) {
            var grid = new LytSlotGrid(1, displays.size());
            for (int i = 0; i < displays.size(); i++) {
                grid.setDisplay(0, i, displays.get(i));
            }
            return grid;
        }

        var nonEmptyDisplays = (int) displays.stream().filter(d -> !isEmpty(d)).count();
        var grid = new LytSlotGrid(1, nonEmptyDisplays);
        var index = 0;
        for (var display : displays) {
            if (!isEmpty(display)) {
                grid.setDisplay(0, index++, display);
            }
        }
        return grid;
    }

    public static LytSlotGrid rowFromDisplays(List<? extends SlotDisplay> displays, boolean skipEmpty) {
        if (!skipEmpty) {
            var grid = new LytSlotGrid(displays.size(), 1);
            for (int i = 0; i < displays.size(); i++) {
                grid.setDisplay(i, 0, displays.get(i));
            }
            return grid;
        }

        var nonEmptyDisplays = (int) displays.stream().filter(d -> !isEmpty(d)).count();
        var grid = new LytSlotGrid(nonEmptyDisplays, 1);
        var index = 0;
        for (var display : displays) {
            if (!isEmpty(display)) {
                grid.setDisplay(index++, 0, display);
            }
        }
        return grid;
    }

    public boolean isRenderEmptySlots() {
        return renderEmptySlots;
    }

    public void setRenderEmptySlots(boolean renderEmptySlots) {
        this.renderEmptySlots = renderEmptySlots;
    }

    @Override
    protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
        // Lay out the slots left-to-right, top-to-bottom
        for (var row = 0; row < height; row++) {
            for (var col = 0; col < width; col++) {
                var index = getSlotIndex(col, row);
                if (index < slots.length) {
                    var slot = slots[index];
                    if (slot != null) {
                        slot.layout(
                                context,
                                x + col * LytSlot.OUTER_SIZE,
                                y + row * LytSlot.OUTER_SIZE,
                                availableWidth);
                    }
                }
            }
        }

        return new LytRect(x, y, LytSlot.OUTER_SIZE * width, LytSlot.OUTER_SIZE * height);
    }

    public void setItem(int x, int y, ItemStack item) {
        setDisplay(x, y, new SlotDisplay.ItemStackSlotDisplay(item));
    }

    public void setIngredient(int x, int y, Ingredient ingredient) {
        setSlot(x, y, new LytSlot(ingredient));
    }

    public void setDisplay(int x, int y, SlotDisplay display) {
        setSlot(x, y, new LytSlot(display));
    }

    private void setSlot(int x, int y, LytSlot newSlot) {
        if (x < 0 || x >= width) {
            throw new IndexOutOfBoundsException("x: " + x);
        }
        if (y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("y: " + y);
        }

        var slotIndex = getSlotIndex(x, y);
        var slot = slots[slotIndex];
        if (slot != null) {
            slot.removeChild(slot);
            slots[slotIndex] = null;
        }

        slot = slots[slotIndex] = newSlot;
        append(slot);
    }

    @Override
    public void render(RenderContext context) {
        // Render empty slots if requested
        if (renderEmptySlots) {
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var index = getSlotIndex(x, y);
                    if (index >= slots.length || slots[index] == null) {
                        context.drawIcon(
                                bounds.x() + LytSlot.OUTER_SIZE * x,
                                bounds.y() + LytSlot.OUTER_SIZE * y,
                                GuiAssets.SLOT_BACKGROUND);
                    }
                }
            }
        }

        super.render(context);
    }

    private int getSlotIndex(int col, int row) {
        return row * width + col;
    }

    private static boolean isEmpty(SlotDisplay d) {
        return d.type() == SlotDisplay.Empty.TYPE;
    }
}
