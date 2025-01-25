
# Changelog

## 2.1.2

- Skip fully invisible blocks (without block entities) when calculating the bounding box of a game scene. Fixes inexplicably larger bounds when blocks like `minecraft:light` where included in the exported structure.

## 2.1.1

- Fix race-condition crash when local file-system changes were processed before the resource reload was finished.

## 2.1.0

- Adds API to open guides for players from both server- and client-side
  - `GuidesCommon.openGuide(Player player, ResourceLocation guideId)` to open the last opened (or start-page if none) page of a guide for the given player.
  - `GuidesCommon.openGuide(Player player, ResourceLocation guideId, PageAnchor anchor)` to open a specific page of a guide for the given player.
- Moves the existing client-only command to `/guidemec`
- Adds a new server-side `/guideme` [command](./commands.md) that allows opening guides for target entities similar to `/tellraw`.
  This can be used to open guides using command blocks and other mechanisms.
  <p><video controls><source src={require('./command-block-guide.mp4').default}/></video></p>
  Example: `/guideme open @s testmod:guide` to open the start page
  or `/guideme open @s testmod:guide page.md#anchor` to open a specific page at an anchor.
- Fix mod version being shown as 0.0.0

## 2.0.1

- Removes superfluous log spam when opening the creative menu.
