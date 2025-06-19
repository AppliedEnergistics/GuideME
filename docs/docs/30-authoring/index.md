---
description: How to create content for a GuideME guide.
---

import ColorPreview from '@site/src/components/ColorPreview';

# Authoring Pages

Pages for a guidebook are read from *all resource packs* across *all namespace*.
That is why each guidebook has its own unique subdirectory, which by default
is `guides/<guide_id_namespace>/<guide_id_path>`. For a guidebook with the id `mod:guide`, this would be
`guides/mod/guide`.
Each file with the extension `.md` in this directory and any subdirectory is considered a page.

:::note

Like all files in Minecraft resource packs, page filenames must
be [valid resource ids](https://minecraft.wiki/w/Resource_location).
Your filenames must all be lowercase, for example.

:::

Pages are written in Markdown. See [supported Markdown](./markdown.md) for details.

Every page should usually declare its title as a level 1 heading at the start (`# Page Title`).

## Frontmatter

Every page can have a header ("frontmatter") that defines metadata for the page in YAML format.

Example:

```yaml
---
navigation:
  title: Page Title
---

# Page Title

Content
```

## Adding Pages to the Navigation Bar

To include a page in the navigation sidebar, it needs to define the `navigation` key in its frontmatter as such:

```yaml
---
navigation:
  # Title shown in the navigation bar
  title: Page Title
  # [OPTIONAL] Item ID for an icon 
  # defaults to the same namespace as the pages, so ae2 in our guidebook
  icon: debug_card
  # [OPTIONAL] The page ID of the parent this page should be sorted under as a child entry
  # If it's in the same namespace as the current page, the namespace can be omitted, otherwise use "ae2:path/to/file.md"
  parent: getting-started.md
---
```

## Declaring Pages as ItemLink targets

When using the `<ItemLink ... />` tag, the guidebook will try to find the page that explains what the given item does.

For this it searches all pages for the `item_ids` frontmatter key. If a page you write should be the primary page
for an item, list it in the `item_ids` frontmatter as such:

```yaml
---
item_ids:
  - ae2:item_id
  - ae2:other_item_id
---
```

Using `<ItemLink id="item_id" />` or `<ItemLink id="ae2:item_id" />` will then link to this page, as will slots
in recipes that show that item.

## Using Images

To show an image, just put it (.png or .jpg) in the `guidebook/assets` folder and embed it either:

* Using a normal Markdown image
* Using `<FloatingImage src="path/to/image.png" align="left or right" />` to have text wrap around the image.
  Use align="left" to wrap text on the right and align="right" to wrap text on the left of the image.
  To insert a break that prevents further text from wrapping from all previous floating images,
  use `<br clear="all" />`.

## Custom Tags

The following custom tags are supported in our Markdown pages.

In all custom tags, item and page ids by default inherit the namespace of the page they're on. So if the
page is in AE2s guidebook, all ids automatically use the `ae2` namespace, unless specified.

### Column / Row Layout

To lay out other tags (such as item images) in a row or column, use the `<Row></Row>`
and `<Column></Column>` tags. You can set a custom gap between items using the `gap` attribute.
It defaults to 5.
The alignment of items perpendicular to the layout axis (for `Column` that means horizontally,
for `Row` vertically), you can use the `alignItems` attribute with the values `start`, `center` and `end`.
Since rows and columns automatically size themselves to their content, you might also have to
expand the size of the row or column to the full page width using `fullWidth={true}` to get the desired effect
of horizontally centering items relative to the page.

Example:

```markdown
<Row>
  <ItemImage id="interface" />
  <ItemImage id="stick" />
</Row>
```

Example for horizontally centering an image on the page:

```markdown
<Column alignItems="center" fullWidth={true}>
  <ItemImage id="interface" />
</Column>
```

### Item Links

To automatically show the translated item name, including an appropriate tooltip, and have the item name link to the
primary guidebook page for that item, use the  `<ItemLink id="item_id" />` tag. The id can omit the guides default
namespace.

[Pages need to be set as the primary target for certain item ids manually](#declaring-pages-as-itemlink-targets).

### Command Links

You can make links that run a command when clicked using `<CommandLink command="/command">text text</CommandLink>`.

The specified command is sent from the client normally and does not bypass permission checks. It has to start with a
slash.

There are optional attributes:

| Attribute | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
| title     | An optional tooltip to show for the link. The command itself will always be shown in the tooltip. |
| close     | If set to `{true}`, the current screen will be closed when the link is clicked.                   |

### Recipes

To show the recipes used to create a certain item, use the `<RecipeFor id="item_id" />` tag.

To show a specific recipe, use the `<Recipe id="recipe/id" />` tag.

All recipe tags support a `fallbackText` attribute to specify the text to show when no recipe(s) can be found. If you just
want to show nothing, you can set `fallbackText=""`.

These tags can be wrapped in a `<Row></Row>` tag to have them automatically wrap and use less vertical space.

:::note

Custom recipe types from mods need special support. See [custom recipe types](../20-integration/recipe-types.md).

:::

### Item Grids

To show-case multiple related items in a grid-layout, use the following markup:

```markdown
<ItemGrid>
  <ItemIcon id="interface" />
  <ItemIcon id="cable_interface" />
</ItemGrid>
```

Similar to `ItemImage` tags, the `ItemIcon` tag also supports additional itemstack data in the `tag` attribute,
in [SNBT format](https://minecraft.wiki/w/NBT_format#SNBT_format).

### Category Index

Pages can further be assigned to be part of multiple categories (orthogonal to the navigation bar).

To do so, specify the following frontmatter key:

```yaml
---
categories:
  - Category 1
  - Category 2
  - Category 3
---
```

A category can contain an unlimited number of pages.

To automatically show a table of contents for a category, use the `<CategoryIndex category="Category 1" />` tag,
and specify the name of the category. It will then display a list of all pages that declare to be part of that
category.

### Sub Pages

This tag will show a list of links to pages. The list will be sourced from the child-pages of
the current page in the navigation-tree. If a specific page-id is given in the `id` attribute, the child-pages of that
page will be shown instead.

The list can be sorted alphabetically (by title) by adding `alphabetical={true}`.

To show the icons associated with each navigation-node, supply `icons={true}`. This does not look very appealing if
some child-pages have icons and others don't.

### Item Images

To show an item, use:

```
<ItemImage id="mod:item_id" />
```

IDs from your own mod don't need to be qualified with the mod id.

The tag also supports the following attributes:

| Attribute | Description                                                                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| tag       | Optional NBT data for the itemstack in [SNBT format](https://minecraft.wiki/w/NBT_format#SNBT_format).                                            |
| scale     | Allows the item image to be scaled. Supports floating point numbers. `scale="1.5"` will show the item at 150% of its natural size.                |
| float     | Allows the item image to be floated like  `FloatingImage` to make it show to the left or right with a block of text. (Allows values: left, right) |

### Block Images

To show a 3d rendering of a block, use:

```
<BlockImage id="mod:block_id" />
```

IDs from your own mod don't need to be qualified with the mod id.

The tag also supports the following attributes:

| Attribute   | Description                                                                                                                                                                                     |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| scale       | Allows the block image to be scaled. Supports floating point numbers. `scale="1.5"` will show at 150% of its normal size.                                                                       |
| float       | Allows the block image to be floated like `FloatingImage` to make it show to the left or right with a block of text. (Allows values: left, right)                                               |
| perspective | Allows the orientation of the block to be changed. By default, the north-east corner of the block will be facing forward. Allowed values: isometric-north-east (default), isometric-north-west. |
| `p:<name>`  | Allows setting arbitrary block state properties on the rendered block, where `<name>` is the name of a block state property.                                                                    |

### Colored Text

:::warning

The following should be used sparingly since it may not provide great contrast when switching between light- and
dark-mode.

:::

You can use the `<Color id="<id>">...</Color>` tag to color text using the following pre-defined symbolic colors:

| ID             | Light-Mode                    | Dark-Mode                     |
|----------------|-------------------------------|-------------------------------|
| `black`        | <ColorPreview color="#000" /> | <ColorPreview color="#000" /> |
| `dark_blue`    | <ColorPreview color="#00A" /> | <ColorPreview color="#00A" /> |
| `dark_green`   | <ColorPreview color="#0A0" /> | <ColorPreview color="#0A0" /> |
| `dark_aqua`    | <ColorPreview color="#0AA" /> | <ColorPreview color="#0AA" /> |
| `dark_red`     | <ColorPreview color="#A00" /> | <ColorPreview color="#A00" /> |
| `dark_purple`  | <ColorPreview color="#A0A" /> | <ColorPreview color="#A0A" /> |
| `gold`         | <ColorPreview color="#AA0" /> | <ColorPreview color="#AA0" /> |
| `gray`         | <ColorPreview color="#AAA" /> | <ColorPreview color="#AAA" /> |
| `dark_gray`    | <ColorPreview color="#555" /> | <ColorPreview color="#555" /> |
| `blue`         | <ColorPreview color="#55F" /> | <ColorPreview color="#55F" /> |
| `green`        | <ColorPreview color="#5F5" /> | <ColorPreview color="#5F5" /> |
| `aqua`         | <ColorPreview color="#5FF" /> | <ColorPreview color="#5FF" /> |
| `red`          | <ColorPreview color="#F55" /> | <ColorPreview color="#F55" /> |
| `light_purple` | <ColorPreview color="#F5F" /> | <ColorPreview color="#F5F" /> |
| `yellow`       | <ColorPreview color="#FF5" /> | <ColorPreview color="#FF5" /> |
| `white`        | <ColorPreview color="#FFF" /> | <ColorPreview color="#FFF" /> |

### Player Name

You can insert the name of the current player by using `<PlayerName />`.

### Key Bindings

You can show the currently binding for a hotkey by using the `<KeyBind id="..." />` tag.

The `id` attribute refers to the key binding by its unique identifier, such as `key.jump` for the jump button.

Ids for default Minecraft keys can be found on the [Minecraft Wiki](https://minecraft.wiki/w/Controls).
