---
description: Which Markdown extensions are supported by GuideME.
---

# Supported Markdown

To get started with Markdown, see the [CommonMark Reference](https://commonmark.org/help/).

## Specifications

GuideME is based on the [micromark](https://github.com/micromark/micromark) Markdown parser, which supports:

- [CommonMark](https://spec.commonmark.org/0.31.2/)
- A subset of [GitHub Flavored Markdown](https://github.github.com/gfm/)
    - [Tables](https://github.github.com/gfm/#tables-extension-)
    - [Strikethrough](https://github.github.com/gfm/#strikethrough-extension-)
- [YAML Frontmatter](https://github.com/micromark/micromark-extension-frontmatter)

## Inline Formatting

| Markdown                   | Alternative       | Result                                         |
|----------------------------|-------------------|------------------------------------------------|
| `*Italic*`                 | `_Italic_`        | ![italic](./markdown/italic.png)               |
| `**Bold**`                 | `__Bold__`        | ![bold](./markdown/bold.png)                   |
| `~~Strikethrough~~`        | `~Strikethrough~` | ![strikethrough](./markdown/strikethrough.png) |
| `[Link](http://a.com)`     |                   | ![link](./markdown/link.png)                   |
| `[Link](./index.md)`       |                   | ![link](./markdown/link.png)                   |
| `[Link](testmod:index.md)` |                   | ![link](./markdown/link.png)                   |
| `` `Inline Code` ``        |                   | ![inline code](./markdown/inline_code.png)     |
| `![Image](test.png)`       |                   | ![image](./markdown/image.png)                 |

## Headings

Headings can be defined by prefixing them with `#`.

```markdown
# Heading 1

## Heading 2

### Heading 3

#### Heading 4

##### Heading 5

###### Heading 6
```

Result:

![headings](./markdown/headings.png)

## Other Block Elements

Horizontal Rule:

Markdown:

```
---
```

Result:

![horizontal rule](./markdown/horizontal_rule.png)

Markdown:

`> Blockquote`

Result:

![horizontal rule](./markdown/blockquote.png)

## Lists

Markdown:

```
- List
- List
- List 

1. One
2. Two
3. Three
```

Result:

![lists](./markdown/lists.png)

## Tables

Markdown:

```
| First Header  | Second Header |
| ------------- | ------------- |
| Content Cell  | Content Cell  |
| Content Cell  | Content Cell  |
```

Result:

![table](./markdown/table.png)
