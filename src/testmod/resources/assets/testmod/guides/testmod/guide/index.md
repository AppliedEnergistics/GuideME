---
item_ids:
  - minecraft:carrot
---

# Start Page

[Japanese](./japanese.md)

[Markdown](./markdown.md)

Welcome to the world of <ItemImage id="minecraft:stone" components="enchantment_glint_override=true" />

You may ~~need~~ a door!

<CommandLink command="/tp @s 0 90 0" title="Tooltip" close={true}>Teleport!</CommandLink>

<GameScene zoom={4} interactive={true}>
    <Entity id="minecraft:sheep" data="{Color: 2}" />
</GameScene>

<RecipeFor id="minecraft:oak_door" />
<Recipe id="minecraft:iron_nugget_from_blasting" />
<RecipeFor id="minecraft:netherite_pickaxe" />
<Recipe id="minecraft:shaper_armor_trim_smithing_template_smithing_trim" />

<GameScene>
  <ImportStructure src="test.nbt" />
</GameScene>

<GameScene zoom="8">
  <ImportStructure src="end_portal.nbt" />
</GameScene>

## Recipes

<Row>
    <RecipeFor id="minecraft:oak_planks" />
    <RecipeFor id="minecraft:red_bed" />
    <RecipeFor id="minecraft:stick" />
    <RecipesFor id="minecraft:green_bed" />
</Row>

***

<Row>
  <BlockImage id="minecraft:oak_log" scale="4" />
  <BlockImage id="minecraft:spruce_log" scale="4" />
  <BlockImage id="minecraft:acacia_log" scale="4" />
  <BlockImage id="minecraft:birch_log" scale="4" />
  <BlockImage id="minecraft:jungle_log" scale="4" />
  <BlockImage id="minecraft:mangrove_log" scale="4" />
</Row>
