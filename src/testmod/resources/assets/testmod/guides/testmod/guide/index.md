---
item_ids:
  - minecraft:carrot
---

# Start Page

[Markdown](./markdown.md)

Welcome to the world of <ItemImage id="minecraft:stone" />

You may ~~need~~ a door!

<CommandLink command="/tp @s 0 90 0" title="Tooltip" close={true}>Teleport!</CommandLink>

<RecipeFor id="minecraft:oak_door" />
<Recipe id="minecraft:iron_nugget_from_blasting" />

<GameScene>
  <ImportStructure src="test.nbt" />
</GameScene>

<GameScene zoom="8">
  <ImportStructure src="end_portal.nbt" />
</GameScene>
