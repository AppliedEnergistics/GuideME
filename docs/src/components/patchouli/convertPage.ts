import {expandFormatting} from "@site/src/components/patchouli/formatting";
import {PatchouliEntry, ZipContent} from "@site/src/components/patchouli/types";
import {extractFile, findRecipeResultItem, relativePageLink, splitIdAndData} from "@site/src/components/patchouli/util";

function convertMultiblock(multiblock: any, writeLogLine: (line: string) => void): string[] {
    const lines: string[] = [];
    lines.push("<GameScene interactive={true} zoom={2}>");

    const {mapping, pattern} = multiblock as {
        mapping: Record<string, string>;
        pattern: string[][];
    };

    for (let y = 0; y < pattern.length; y++) {
        const row = pattern[y];
        for (let z = 0; z < row.length; z++) {
            const col = row[z];
            for (let x = 0; x < col.length; x++) {
                const patternCh = col[x];
                if (patternCh === ' ') {
                    continue;
                }

                let mapped = mapping[patternCh];
                if (!mapped) {
                    if (patternCh !== '0') {
                        writeLogLine('Unknown mapping character in multiblock ' + patternCh);
                    }
                    continue;
                }

                // Conver the block property list to tag properties
                let startOfProps = mapped.indexOf('[');
                let propertiesAttr = '';
                if (startOfProps !== -1 && mapped.endsWith("]")) {
                    const properties = mapped.substring(startOfProps + 1, mapped.length - 1).split(",");
                    mapped = mapped.substring(0, startOfProps);
                    propertiesAttr = properties.map(p => {
                        let [key, value] = p.split("=", 2);
                        if (value.startsWith('"') && value.endsWith('"')
                            || value.startsWith('\'') && value.endsWith('\'')) {
                            value = value.substring(1, value.length - 1);
                        }

                        return `p:${key}="${value}"`;
                    }).join(" ");
                }

                lines.push(`  <Block x="${x}" y="${y}" z="${z}" id="${mapped}"${propertiesAttr} />`);
            }
        }
    }

    lines.push("</GameScene>");
    return lines;
}

export async function convertPage(zipContent: ZipContent,
                                  macros: Record<string, string>,
                                  pages: Record<string, PatchouliEntry>,
                                  bookNamespace: string,
                                  bookId: string,
                                  pageId: string,
                                  page: PatchouliEntry,
                                  language: string | undefined,
                                  writeLogLine: (line: string) => void,
                                  outputFiles: Record<string, Uint8Array | string>) {

    const pagePath = `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/${pageId}.md`;
    const translatedPagePath = language ? `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/_${language}/${pageId}.md` : pagePath;
    const pageDir = pagePath.substring(0, pagePath.lastIndexOf("/"));
    let lines: string[] = [];

    const pageItemIds: string[] = [];
    const frontmatter: string[] = [];

    // Frontmatter
    frontmatter.push("navigation:");
    frontmatter.push("  title: " + JSON.stringify(page.name));
    frontmatter.push("  icon: " + JSON.stringify(page.icon));
    if (page.sortnum) {
        frontmatter.push("  position: " + page.sortnum);
    }
    if (page.category) {
        frontmatter.push("  parent: " + page.category + ".md");
    }

    // Body
    lines.push(`# ${page.name}`)
    lines.push("");

    async function writeRecipe(recipeId: string) {
        lines.push(`<Recipe id="${recipeId}" />`);
        lines.push("");
        const resultItem = await findRecipeResultItem(zipContent, recipeId, writeLogLine);
        if (resultItem) {
            pageItemIds.push(resultItem);
            writeLogLine(` Setting page ${pageId} as target for item ${resultItem}`);
        }
    }

    for (let patchouliPage of page.pages) {
        // Handle implicit text pages
        if (typeof patchouliPage === "string") {
            patchouliPage = {
                type: "text",
                text: patchouliPage
            };
        }

        if (patchouliPage.anchor) {
            lines.push(`<a name="${patchouliPage.anchor}"></a>`);
        }

        let type = patchouliPage.type;
        if (!type.includes(":")) {
            type = "patchouli:" + type;
        }

        switch (type) {
            case "patchouli:text":
                if (patchouliPage.title) {
                    lines.push("## " + patchouliPage.title);
                    lines.push("");
                }
                lines.push(patchouliPage.text);
                lines.push("");
                break;
            case "patchouli:multiblock":
                if (patchouliPage.name) {
                    lines.push("## " + patchouliPage.name);
                    lines.push("");
                }
                if (patchouliPage.multiblock_id) {
                    lines.push("TODO Multiblock-ID: " + patchouliPage.multiblock_id);
                    lines.push("");
                }
                if (patchouliPage.multiblock) {
                    lines.push(...convertMultiblock(patchouliPage.multiblock, writeLogLine));
                    lines.push("");
                }
                lines.push(patchouliPage.text);
                lines.push("");
                break;
            case "patchouli:entity":
                if (patchouliPage.name) {
                    lines.push("## " + patchouliPage.name);
                } else {
                    lines.push("## " + patchouliPage.entity + " (TODO)");
                }

                lines.push("");
                const zoom = patchouliPage.scale ? (4 * parseFloat(patchouliPage.scale)) : 4;
                lines.push(`<GameScene zoom={${zoom}}>`);
                const rotationAttr = patchouliPage.default_rotation ? ` rotationY={${patchouliPage.default_rotation}}` : '';
                const offsetAttr = patchouliPage.offset ? ` y={${patchouliPage.offset}}` : '';
                lines.push(`  <Entity id="${patchouliPage.entity}"${rotationAttr}${offsetAttr} />`);
                lines.push("</GameScene>");
                if (patchouliPage.text) {
                    lines.push("");
                    lines.push(patchouliPage.text);
                }
                lines.push("");
                break;
            case "patchouli:link":
                if (patchouliPage.title) {
                    lines.push("## " + patchouliPage.title);
                    lines.push("");
                }
                if (patchouliPage.text) {
                    lines.push(patchouliPage.text);
                    lines.push("");
                }
                lines.push("[" + patchouliPage.link_text + "](" + patchouliPage.url + ")");
                break;
            case "patchouli:image":
                if (patchouliPage.title) {
                    lines.push("## " + patchouliPage.title);
                    lines.push("");
                }
                lines.push(patchouliPage.text);
                lines.push("");
                if (patchouliPage.border) {
                    lines.push("TODO: Unsupported flag 'border'");
                }
                for (const image of patchouliPage.images) {
                    // Copy the image over
                    let [namespace, path] = (image as string).split(":");
                    if (!path) {
                        path = namespace;
                        namespace = bookNamespace;
                    }
                    let pathInZip = `assets/${namespace}/${path}`;
                    const imageContent = zipContent[pathInZip];
                    if (!imageContent) {
                        writeLogLine("Cannot copy image from non-book namespace: " + image);
                        lines.push("TODO: Missing image " + image);
                        lines.push("")
                    } else {
                        const filename = path.substring(path.lastIndexOf("/") + 1);
                        outputFiles[`${pageDir}/${filename}`] = await extractFile(zipContent, pathInZip);
                        lines.push(`![](${filename})`);
                        lines.push("");
                    }
                }
                break;
            case "patchouli:crafting":
            case "patchouli:smelting":
                if (patchouliPage.title) {
                    lines.push("## " + patchouliPage.title);
                    lines.push("");
                }
                lines.push(patchouliPage.text);
                lines.push("");
                if (patchouliPage.recipe) {
                    await writeRecipe(patchouliPage.recipe);
                }
                if (patchouliPage.recipe2) {
                    await writeRecipe(patchouliPage.recipe2);
                }
                break;
            case "patchouli:spotlight":
                if (patchouliPage.link_recipe) {
                    pageItemIds.push(splitIdAndData(patchouliPage.item)[0]);
                    writeLogLine(` Setting page ${pageId} as target for item ${patchouliPage.item}`);
                }
                if (patchouliPage.title) {
                    lines.push("## " + patchouliPage.title);
                    lines.push("");
                }
                lines.push(`<ItemImage id="${patchouliPage.item}" />`)
                lines.push("");
                lines.push(patchouliPage.text);
                lines.push("");
                if (patchouliPage.recipe) {
                    await writeRecipe(patchouliPage.recipe);
                }
                if (patchouliPage.recipe2) {
                    await writeRecipe(patchouliPage.recipe2);
                }
                break;
            case "patchouli:relations":
                lines.push("## " + (patchouliPage.title ?? "Related Chapters"));
                lines.push("");
                for (const entry of patchouliPage.entries) {
                    // TODO: Resolve the name of the target page in the right language
                    lines.push(`- [TODO](${relativePageLink(pageId, entry)})`);
                }
                lines.push("");
                lines.push(patchouliPage.text);
                lines.push("");
                break;
            case "patchouli:empty":
                // Render as thematic break
                lines.push("");
                lines.push("---");
                lines.push("");
                break;
            default:
                lines.push(`**TODO:** Unsupported Patchouli page type **${type}**`);
                lines.push("");
                lines.push("```");
                lines.push(JSON.stringify(patchouliPage));
                lines.push("```");
                lines.push("");
                writeLogLine(`Unsupported type ${type} on page ${pageId}`);
                break;
        }
    }

    const body = expandFormatting(pageId, lines.join("\n") + "\n", macros, writeLogLine);

    if (pageItemIds.length) {
        frontmatter.push("item_ids:");
        const uniqueItemIds = new Set<string>(pageItemIds);
        for (const id of uniqueItemIds) {
            frontmatter.push("  - " + id);
        }
    }

    const pageContent = "---\n" + frontmatter.join("\n") + "\n---\n\n" + body;
    outputFiles[translatedPagePath] = pageContent;
}
