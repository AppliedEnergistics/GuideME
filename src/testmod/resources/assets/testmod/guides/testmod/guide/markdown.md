# Markdown Test

## Inline Formatting

| Markdown                            | Alternative       | Result                            |
|-------------------------------------|-------------------|-----------------------------------|
| `*Italic*`                          | `_Italic_`        | *Italic*                          |
| `**Bold**`                          | `__Bold__`        | **Bold**                          |
| `~~Strikethrough~~`                 | `~Strikethrough~` | ~~Strikethrough~~                 |
| `[Link](http://a.com)`              |                   | [Link](http://a.com)              |
| `[Relative Link](./index.md)`       |                   | [Link](./index.md)                |
| `[Absolute Link](testmod:index.md)` |                   | [Absolute Link](testmod:index.md) |
| `` `Inline Code` ``                 |                   | `Inline Code`                     |
| `![Image](test.png)`                |                   | ![Image](test.png)                |

## Headings

Headings can be defined by prefixing them with `#`.

# Heading 1

## Heading 2

### Heading 3

#### Heading 4

##### Heading 5

###### Heading 6

## Other Block Elements

Horizontal Rule:

Markdown:

```
---
```

Result:

---

Markdown:

`> Blockquote`

Result:

> Blockquote 

## Lists

Markdown:

```
* List
* List
* List 

1. One
2. Two
3. Three
```

Result:

* List
* List
* List 

1. One
2. Two
3. Three

## Tables

Markdown:

```
| First Header  | Second Header |
| ------------- | ------------- |
| Content Cell  | Content Cell  |
| Content Cell  | Content Cell  |
```

Result:

| First Header  | Second Header |
| ------------- | ------------- |
| Content Cell  | Content Cell  |
| Content Cell  | Content Cell  |
