package guideme.siteexport;

import java.util.List;
import java.util.function.Consumer;

import guideme.siteexport.web.HTMLNode;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface RecipeWebRenderingContext extends WebRenderingContext {

    /**
     * Add a standard recipe box, which has a Minecraft-style frame, and shows an item-icon for the crafting station in
     * its header alongside a title. The name of an item will be shown as the tooltip.
     */
    RecipeBoxBuilder recipeBox(String craftingStationItemId, String title, String tooltipItemId);

    /**
     * Add a standard recipe box, which has a Minecraft-style frame, and shows the given HTML in its header.
     */
    RecipeBoxBuilder recipeBox(HTMLNode header);

    HTMLNode slotHtml(String itemId);

    HTMLNode slotHtml(List<String> itemIds);

    HTMLNode arrowHtml();

    interface RecipeBoxBuilder {
        /**
         * Creates a standard ingredient grid using the same logic used for Vanilla crafting recipes. The exported
         * recipe is expected to have an {@code ingredient} list. If the {@code shapeless} field is set to {@code true},
         * then no width is required, but if it is missing or {@code false}, the width of the recipe grid must have been
         * encoded in {@code width}.
         */
        RecipeBoxBuilder craftingIngredientGrid();

        RecipeBoxBuilder shapelessSlots(List<List<String>> ingredients);

        RecipeBoxBuilder arrow();

        /**
         * Adds a result slot with the current recipes result.
         */
        RecipeBoxBuilder resultSlot();

        default RecipeBoxBuilder slot(String itemId) {
            return slot(List.of(itemId));
        }

        RecipeBoxBuilder slot(List<String> itemIds);

        /**
         * Appends a HTML node.
         */
        RecipeBoxBuilder append(HTMLNode node);

        /**
         * Creates a flexible grid where each cell can be filled individually.
         * @param columns The number of columns in the grid.
         * @param rows The number of rows in the grid.
         */
        RecipeBoxBuilder grid(int columns, int rows, Consumer<GridBuilder> customizer);

        void build();
    }

    interface GridBuilder {
        GridBuilder image(int column, int row, String assetName);

        default GridBuilder slot(int column, int row, String itemId) {
            return slot(column, row, List.of(itemId));
        }

        GridBuilder slot(int column, int row, List<String> itemId);

        GridBuilder set(int column, int row, HTMLNode node);
    }
}
