---
item_ids:
  - minecraft:carrot
---

# Start Page

[Japanese](./japanese.md)

[Markdown](./markdown.md)

<Recipe id="missingrecipe" fallbackText="The recipe for special item is disabled." />

Welcome to the world of <ItemImage id="minecraft:stone" components="enchantment_glint_override=true" />, <PlayerName />!

Keybinding Test: <KeyBind id="key.jump" />. Unbound key: <KeyBind id="key.spectatorOutlines" />.

You may ~~need~~ a <Color color="#ff0000">door</Color> <Color id="test_color">door</Color>!

<CommandLink command="/tp @s 0 90 0" title="Tooltip" close={true}>Teleport!</CommandLink>

<GameScene zoom={4} interactive={true}>
    <Entity id="minecraft:sheep" data="{Color: 2}" />
</GameScene>

<RecipeFor id="minecraft:oak_door" />
<Recipe id="minecraft:iron_nugget_from_blasting" />

<GameScene zoom={2}>
  <ImportStructure src="test.nbt" />

  <BlockAnnotationTemplate id="minecraft:stripped_spruce_log" p:axis="x">
    <DiamondAnnotation pos="0.5 0.5 0.5" color="#ff0000">
      This will be shown in the tooltip! <ItemImage id="minecraft:stone" />
    </DiamondAnnotation>
  </BlockAnnotationTemplate>
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
