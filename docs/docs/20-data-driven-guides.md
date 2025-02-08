# Data Driven Guides

You can create complete guides using only resource packs. You can find an example of this in
the [GuideME test mod](https://github.com/AppliedEnergistics/GuideME/tree/main/src/testmod/resources).

## Guide Definition

To create the guide itself, you need to assign it a unique id of the form `<namespace>:<path>`.
For example, `myrp:guide` if the resource namespace of your resource pack is `myrp`.

To create the guide, you need to place a JSON file in your resource pack at `assets/myrp/guideme_guides/guide.json`.

Here is an example for this file:

```json
{
  "item_settings": {
    "display_name": {
      "type": "translatable",
      "translate": "testmod.guide_name"
    },
    "tooltip_lines": [
      {
        "text": "Best guide ever!",
        "color": "dark_gray"
      }
    ],
    "model": "testmod:item/guide"
  }
}
```

## Generic Guide Item

GuideME contains a generic guide item (`guideme:guide`) which you can use to access your guide.

Use the following syntax to give yourself a guide which will open `myrp:guide` and assume the name,
item model and description specified in the data driven guide file (see above):

```
/give @s guideme:guide[guideme:guide_id="myrp:guide"]
```

The `item_settings` section of the guide JSON file allows you to set both the name of
the item and additional tooltip lines in the JSON text component format.

All settings of the `item_settings` block are optional. If you do not set a `display_name`,
the name will just be "Guide". If you don't set a model, it will use the default GuideME model.

## Writing Pages

See [authoring pages](30-authoring/index.md).
