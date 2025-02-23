# Translating Guides

GuideME will look for translated guidebook content in a language-code folder at the root of the guidebook.

To translate a page `folder/page.md` into another language, place the translated content into
`_<langcode>/folder/page.md` (for example `_de_de/folder/page.md`).

If a translated page cannot be found, the page from the default page will be loaded instead.

The same rules outlines for pages also apply to assets such as images. GuideME will first try to load an asset
from the language-specific folder and then fall back to loading it from the default folder.

[Full-text search](./40-search.md) also tries to apply language-specific rules for the most common languages,
and falls back to english parsing rules if no language-specific rules are supported.
