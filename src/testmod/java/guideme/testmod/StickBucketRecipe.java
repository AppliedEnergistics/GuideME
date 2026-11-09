package guideme.testmod;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Custom test recipe that combines a stick with either a water bucket (-> mossy cobblestone) or a lava bucket (-> magma
 * block). It exposes one recipe display per accepted combination.
 */
public class StickBucketRecipe extends CustomRecipe {
    public static final StickBucketRecipe INSTANCE = new StickBucketRecipe();

    public static final RecipeSerializer<StickBucketRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE),
            StreamCodec.unit(INSTANCE));

    private StickBucketRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return getResult(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        var result = getResult(input);
        return result != null ? new ItemStack(result) : ItemStack.EMPTY;
    }

    @Nullable
    private static Item getResult(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }

        boolean hasStick = false;
        Item result = null;
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.STICK) && !hasStick) {
                hasStick = true;
            } else if (stack.is(Items.WATER_BUCKET) && result == null) {
                result = Items.MOSSY_COBBLESTONE;
            } else if (stack.is(Items.LAVA_BUCKET) && result == null) {
                result = Items.MAGMA_BLOCK;
            } else {
                return null;
            }
        }

        return hasStick ? result : null;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                createDisplay(Items.WATER_BUCKET, Items.MOSSY_COBBLESTONE),
                createDisplay(Items.LAVA_BUCKET, Items.MAGMA_BLOCK));
    }

    private static RecipeDisplay createDisplay(Item bucket, Item result) {
        return new ShapelessCraftingRecipeDisplay(
                List.of(new SlotDisplay.ItemSlotDisplay(Items.STICK), new SlotDisplay.ItemSlotDisplay(bucket)),
                new SlotDisplay.ItemSlotDisplay(result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE));
    }

    @Override
    public RecipeSerializer<StickBucketRecipe> getSerializer() {
        return SERIALIZER;
    }
}
