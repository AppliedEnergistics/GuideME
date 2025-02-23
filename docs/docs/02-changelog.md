
import Video from '@site/src/components/Video';

# Changelog

## 21.1.1 (Minecraft 1.21.1)

- Added support for [translating guides](./60-translation.md).
- Added an option for Players to disable loading of guide translations.
- Added support for the strikethrough Markdown extension (`~~text~~` or `~text~`).
- Added rendering for Markdown blockquotes.
- Added support for entities in game scenes using `<Entity />`. See [game scenes](./30-authoring/game-scenes.md#entities) for details.
  ![entity in game scene](./30-authoring/game-scene-entity.png)
- Add `<CommandLink command="/command" [title="tooltip"] [close={true}]>...</CommandLink>` that runs a command when clicked.
- Added a tag for coloring text using pre-defined colors, for use with the 16 default Minecraft colors.
- Fix several search issues relating to not analyzing queries properly, which reduced the number of relevant results.
- Fix returning to the original screen when the guide is closed.
- Improved visibility of the debug overlay text and outlines.
- Fix background panels in guide being drawn without depth test enabled, sometimes hiding other elements.
- Fix "Crafting (shapeless)" sometimes overflowing the recipe box and design an easier to use API for
  integrating custom recipe types.
- Fix a hidden navbar in guides without navigation still blocking interaction with elements below.
- Floating point attributes to custom tags can now be specified using MDX expression syntax too (i.e.: `<GameScene zoom={2.5}>`). 
  Please note that only bare floating point values are supported, no actual expressions.   
- Fatal Markdown parsing errors will now no longer crash the resource reload and instead replace the offending page with an error page.
- Cycles in the navigation tree will now be reported and no longer cause a stack overflow during resource reload.

## 21.1.0 (Minecraft 1.21.1)

- Switching to the NeoForge versioning scheme, this version is equivalent to version 2.6.0, except for the following changes.
- Improved query parsing for full-text search. Search will now always apply "incremental" search for the last entered word,
  assuming the user might not have entered it fully yet. This means searching for "io po" will search for both "io po"
  and "io po*", although it will score an exact hit for "po" higher than a hit for "port" (for example).
- Fix parsing of links to pages where the mod-id contained underscores (i.e. `modern_industrialization:some_page.md`).
- Fix tooltip crash caused by wrong access transformer.

## 2.6.0 (Minecraft 1.21.1)

- Change the default layout of guides to be a centered column, and add a toolbar button to toggle between 
  full-width and centered-column layout.
  <Video src="center-column-layout.mp4" />
- Fix navigating between guide pages not appending to navigation history
- Add configuration screen
- Make scaling of Guide UI independent of UI scale for scales 1 and 3, where the uniform Minecraft font
  has severe rendering issues. This behavior can be disabled in the config screen (Adaptive Scaling).
- Added support for blast furnace recipes
- Do not show a navigation bar for guides that do not have any navigation items

## 2.5.1 (Minecraft 1.21.1)

- Fix shared recipe types not being collected correctly from the service loader

## 2.5.0 (Minecraft 1.21.1)

- Added an extension point for mods to add support for [custom recipe types](20-integration/recipe-types.md) to all guides
- Fixed an issue with navigating to the search screen

## 2.4.0 (Minecraft 1.21.1)

- Add missing Markdown node classes to API jar
- Add structure editing commands that only work in singleplayer:
    - `/guideme placeallstructures x y z` will place all structures found in all guidebooks
    - `/guideme placeallstructures x y z <guide>` will place all structures found in a given guidebook
    - `/guideme importstructure <origin>` opens a system file open dialog and places the selected structure file at the given origin
    - `/guideme exportstructure <origin> <size>` opens a system file save dialog and exports the given bounds as a structure file at the chosen location
- Fixes a resource reload crash when a page references a non-existing item as its navigation icon
- Added op command `/guideme give <target> <guide>` to quickly give a guide item to an entity target (i.e. `@s`)
- Fix guidebook navbar closing when clicking links

## 2.3.1 (Minecraft 1.21.1)

- Fixes a crash with the generic guide item if it has no guide id attached

## 2.3.0 (Minecraft 1.21.1)

- GuideME is now published on Maven Central instead of Modmaven
- The group id of the Maven artifact has changed from `appeng` to `org.appliedenergistics` 
  to enable publishing on Maven Central

## 2.2.0 (Minecraft 1.21.1)

- Added full-text search based on Apache Lucene, which is enabled for all guides:
  <Video src="guide-search.mp4" />
- Added `alignItems="start|center|end"` to the `Row` and `Column` tags to align content along the layout axis
- Added `fullWidth={true}` to the `Row` and `Column` axis to stretch them to the full width
- This enables `<Column alignItems="center" fullWidth={true}>...</Column>` to center content like images horizontally
- Fix the navigation bar sometimes opening and closing very slowly
- Store the navigation history on a per-guide basis, fixing "page not found" errors when switching back and forth between different guides.
- To limit the increase in Jar size due to Lucenes rather large volume, starting with this release,
  Proguard is enabled to strip unused parts of GuideMEs bundled dependencies (this saves about 5MB). There
  is some potential for `ClassNotFoundErrors` in cases where we missed required Proguard configuration.
  Please let us know if you find any errors!
- API additions
  - `ConstantColor#TRANSPARENT`
  - Added `index` method to `TagCompiler` to allow custom tags to control how they are indexed by search
    By default, all custom tags simply add their children to the indexer
  - Added the ability to set borders for `LytBox`
  - Generalized `GuideUiHost` into `DocumentUiHost`

## 2.1.2 (Minecraft 1.21.1)

- Skip fully invisible blocks (without block entities) when calculating the bounding box of a game scene. Fixes inexplicably larger bounds when blocks like `minecraft:light` where included in the exported structure.

## 2.1.1 (Minecraft 1.21.1)

- Fix race-condition crash when local file-system changes were processed before the resource reload was finished.

## 2.1.0 (Minecraft 1.21.1)

- Adds API to open guides for players from both server- and client-side
  - `GuidesCommon.openGuide(Player player, ResourceLocation guideId)` to open the last opened (or start-page if none) page of a guide for the given player.
  - `GuidesCommon.openGuide(Player player, ResourceLocation guideId, PageAnchor anchor)` to open a specific page of a guide for the given player.
- Moves the existing client-only command to `/guidemec`
- Adds a new server-side `/guideme` [command](./40-commands.md) that allows opening guides for target entities similar to `/tellraw`.
  This can be used to open guides using command blocks and other mechanisms.
  <Video src="command-block-guide.mp4" />
  Example: `/guideme open @s testmod:guide` to open the start page
  or `/guideme open @s testmod:guide page.md#anchor` to open a specific page at an anchor.
- Fix mod version being shown as 0.0.0

## 2.0.1 (Minecraft 1.21.1)

- Removes superfluous log spam when opening the creative menu.
