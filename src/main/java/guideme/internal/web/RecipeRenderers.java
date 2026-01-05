package guideme.internal.web;

import guideme.siteexport.ExportedRecipe;
import guideme.siteexport.RecipeWebRenderer;
import guideme.siteexport.RecipeWebRenderingContext;
import guideme.siteexport.web.HTMLNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class CraftingRecipeRenderer implements RecipeWebRenderer {
    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("minecraft:crafting");
    }

    @Override
    public void render(RecipeWebRenderingContext builder, ExportedRecipe recipe) {
        var shapeless = (boolean) recipe.fields().get("shapeless");

        var type = "Crafting";
        if (shapeless) {
            type += " (Shapeless)";
        }

        builder.recipeBox("minecraft:crafting_table", type, recipe.resultItem())
                .craftingIngredientGrid()
                .arrow()
                .slot(recipe.resultItem())
                .build();
    }
}

final class SmeltingRecipeRenderer implements RecipeWebRenderer {
    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("minecraft:smelting", "minecraft:blasting");
    }

    @Override
    public void render(RecipeWebRenderingContext builder, ExportedRecipe recipe) {
        var ingredient = recipe.getIngredient("ingredient");

        var craftingStation = "minecraft:furnace";
        if (recipe.type().equals("minecraft:blasting")) {
            craftingStation = "minecraft:blast_furnace";
        }

        builder.recipeBox(craftingStation, "Smelting", recipe.resultItem())
                .append(HTMLNode.tag("div")
                            .setClassName("smelting-input-box")
                                .append(builder.slotHtml(ingredient))
                                .append(HTMLNode.tag("div").setClassName("fire")))
                .arrow()
                .slot(recipe.resultItem())
                .build();
    }
}

final class SmithingRecipeRenderer implements RecipeWebRenderer {
    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("minecraft:smithing");
    }

    @Override
    public void render(RecipeWebRenderingContext builder, ExportedRecipe recipe) {
        var base = recipe.getIngredient("base");
        var addition = recipe.getIngredient("addition");
        var template = recipe.getIngredient("template");

        builder.recipeBox("minecraft:smithing_table", "Smithing", recipe.resultItem())
                .shapelessSlots(List.of(base, addition, template))
                .arrow()
                .slot(recipe.resultItem())
                .build();
    }
}
