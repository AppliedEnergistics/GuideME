
# Changelog

## 2.2.0

- Added full-text search based on Apache Lucene, which is enabled for all guides:
  <p><video controls><source src={require('./guide-search.mp4').default}/></video></p>
- Added `alignItems="start|center|end"` to the `Row` and `Column` tags to align content along the layout axis.
- Added `fullWidth={true}` to the `Row` and `Column` axis to stretch them to the full width.
- This enables `<Column alignItems="center" fullWidth={true}>...</Column>` to center content like images horizontally.
- Fix the navigation bar sometimes opening and closing very slowly.
- Store the navigation history on a per-guide basis, fixing "page not found" errors when switching back and forth between different guides.
- To limit the increase in Jar size due to Lucenes rather large volume, starting with this release,
  Proguard is enabled to strip unused parts of GuideMEs bundled dependencies (this saves about 5MB). There
  is some potential for `ClassNotFoundErrors` in cases where we missed required Proguard configuration.
  Please let us know if you find any errors!

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
