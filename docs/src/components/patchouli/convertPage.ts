import {expandFormatting} from "@site/src/components/patchouli/formatting";
import {PatchouliEntry, ZipContent} from "@site/src/components/patchouli/types";
import {extractFile, findRecipeResultItem, relativePageLink, splitIdAndData} from "@site/src/components/patchouli/util";
import {convertMultiblock} from "@site/src/components/patchouli/convertMultiblock";
import {cropPngTo200x200} from "@site/src/components/patchouli/cropImage";

export async function convertPage(zipContent: ZipContent,
                                  macros: Record<string, string>,
                                  pages: Record<string, PatchouliEntry>,
                                  bookNamespace: string,
                                  bookId: string,
                                  pageId: string,
                                  page: PatchouliEntry,
                                  language: string | undefined,
                                  translate: (text: string) => string,
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
    frontmatter.push("  title: " + JSON.stringify(translate(page.name)));
    frontmatter.push("  icon: " + JSON.stringify(page.icon));
    if (page.sortnum) {
        frontmatter.push("  position: " + page.sortnum);
    }
    if (page.category) {
        frontmatter.push("  parent: " + page.category + ".md");
    }

    // Body
    lines.push(`# ${translate(page.name)}`)
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
                    lines.push("## " + translate(patchouliPage.title));
                    lines.push("");
                }
                lines.push(translate(patchouliPage.text));
                lines.push("");
                break;
            case "patchouli:multiblock":
                if (patchouliPage.name) {
                    lines.push("## " + translate(patchouliPage.name));
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
                lines.push(translate(patchouliPage.text));
                lines.push("");
                break;
            case "patchouli:entity":
                let entityId: string = patchouliPage.entity;

                const startOfNbt = entityId.indexOf("{");
                let dataAttr = '';
                if (startOfNbt !== -1 && entityId.endsWith("}")) {
                    dataAttr = ` data="${entityId.substring(startOfNbt)}"`;
                    entityId = entityId.substring(0, startOfNbt);
                }
                
                if (patchouliPage.name) {
                    lines.push("## " + translate(patchouliPage.name));
                } else {
                    lines.push("## " + entityId + " (TODO)");
                }

                lines.push("");
                const zoom = patchouliPage.scale ? (4 * parseFloat(patchouliPage.scale)) : 4;
                lines.push(`<GameScene zoom={${zoom}}>`);
                const rotationAttr = patchouliPage.default_rotation ? ` rotationY={${patchouliPage.default_rotation}}` : '';
                const offsetAttr = patchouliPage.offset ? ` y={${patchouliPage.offset}}` : '';
                lines.push(`  <Entity id="${entityId}"${rotationAttr}${offsetAttr}${dataAttr} />`);
                lines.push("</GameScene>");
                if (patchouliPage.text) {
                    lines.push("");
                    lines.push(translate(patchouliPage.text));
                }
                lines.push("");
                break;
            case "patchouli:link":
                if (patchouliPage.title) {
                    lines.push("## " + translate(patchouliPage.title));
                    lines.push("");
                }
                if (patchouliPage.text) {
                    lines.push(translate(patchouliPage.text));
                    lines.push("");
                }
                lines.push("[" +translate(patchouliPage.link_text) + "](" + patchouliPage.url + ")");
                break;
            case "patchouli:image":
                if (patchouliPage.title) {
                    lines.push("## " + translate(patchouliPage.title));
                    lines.push("");
                }
                lines.push(translate(patchouliPage.text));
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
                        let imageContent = await extractFile(zipContent, pathInZip);
                        try {
                            imageContent = await cropPngTo200x200(imageContent);
                        } catch (e) {
                            writeLogLine(`Failed to crop image ${pathInZip} to 200x200: ${e}. Using original image instead.`);
                        }

                        const filename = path.substring(path.lastIndexOf("/") + 1);
                        outputFiles[`${pageDir}/${filename}`] = imageContent;
                        lines.push(`![](${filename})`);
                        lines.push("");
                    }
                }
                break;
            case "patchouli:crafting":
            case "patchouli:smelting":
            case "patchouli:blasting":
                if (patchouliPage.title) {
                    lines.push("## " + translate(patchouliPage.title));
                    lines.push("");
                }
                lines.push(translate(patchouliPage.text));
                lines.push("");
                if (patchouliPage.recipe) {
                    await writeRecipe(patchouliPage.recipe);
                }
                if (patchouliPage.recipe2) {
                    await writeRecipe(patchouliPage.recipe2);
                }
                break;
            case "patchouli:spotlight":
                if (patchouliPage.title) {
                    lines.push("## " + translate(patchouliPage.title));
                    lines.push("");
                }
                for (const [item,] of splitIdAndData(patchouliPage.item)) {
                    if (patchouliPage.link_recipe) {
                        pageItemIds.push(item);
                        writeLogLine(` Setting page ${pageId} as target for item ${patchouliPage.item}`);
                    }
                    lines.push(`<ItemImage id="${item}" />`)
                }
                lines.push("");
                lines.push(translate(patchouliPage.text));
                lines.push("");
                if (patchouliPage.recipe) {
                    await writeRecipe(patchouliPage.recipe);
                }
                if (patchouliPage.recipe2) {
                    await writeRecipe(patchouliPage.recipe2);
                }
                break;
            case "patchouli:relations":
                if (patchouliPage.title) {
                    lines.push("## " + translate(patchouliPage.title));
                } else {
                    lines.push("## Related Chapters");
                }
                lines.push("");
                for (const entry of patchouliPage.entries) {
                    // TODO: Resolve the name of the target page in the right language
                    lines.push(`- [TODO](${relativePageLink(pageId, entry)})`);
                }
                lines.push("");
                lines.push(translate(patchouliPage.text));
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
