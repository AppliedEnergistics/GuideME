
import Video from '@site/src/components/Video';

# Commands

## Open Guides

This command opens guides for any player (using an [entity target selector](https://minecraft.wiki/w/Target_selectors)).

It requires operator permissions since it can open guides for other players.

`/guideme open <target> <guide-id>`

`/guideme open <target> <guide-id> <page-id>`

`/guideme open <target> <guide-id> <page-id>#<anchor>`

This can be used to open guides using command blocks:

<Video src="command-block-guide.mp4" />

## Open Guides (client only)

This command on your own client only and opens any guide for yourself.

`/guidemec open <guide-id>`

`/guidemec open <guide-id> <page-id>`

`/guidemec open <guide-id> <page-id>#<anchor>`

## Create Guide Items

The following commands will create a generic guide item that opens the given guide when used.

`/guideme give <entity-target> <guide-id>`

For example `/guideme give @s ae2:guide`
