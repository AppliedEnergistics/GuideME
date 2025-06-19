---
item_ids:
  - minecraft:carrot
---

# Start Page

[Markdown](./markdown.md)

<Recipe id="missingrecipe" fallbackText="The recipe for special item is disabled." />

Welcome to the world of <ItemImage id="minecraft:stone" />, <PlayerName />!

Keybinding Test: <KeyBind id="key.jump" />. Unbound key: <KeyBind id="key.spectatorOutlines" />.

You may ~~need~~ a <Color color="#ff0000">door</Color> <Color id="test_color">door</Color>!

<CommandLink command="/tp @s 0 90 0" title="Tooltip" close={true}>Teleport!</CommandLink>

<GameScene zoom={4} interactive={true}>
    <Entity id="minecraft:sheep" data="{Color: 2}" />
</GameScene>

<RecipeFor id="minecraft:oak_door" />
<Recipe id="minecraft:iron_nugget_from_blasting" />

<GameScene>
  <ImportStructure src="test.nbt" />
</GameScene>

<GameScene zoom="8">
  <ImportStructure src="end_portal.nbt" />
</GameScene>
