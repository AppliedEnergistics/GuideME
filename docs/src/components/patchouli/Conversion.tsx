import {FileWithPath} from "react-dropzone";
import {useEffect, useState} from "react";
import {unzip, ZipItem} from 'but-unzip';
import OutputFilesBrowser from "./OutputFilesBrowser";
import DownloadZipFile from "@site/src/components/patchouli/DownloadZipFile";
import {expandFormatting} from "@site/src/components/patchouli/formatting";

type ZipContent = {
    [p: string]: ZipItem
};

type PatchouliCategory = {
    name: string;
    description: string;
    icon: string;
    parent?: string;
    flag?: string;
    sortnum?: number;
    secret?: boolean;
}
type PatchouliEntry = {
    name: string;
    category: string;
    icon: string;
    pages: PatchouliEntryPage[];
    advancement?: string;
    flag?: string;
    read_by_default?: boolean;
    sortnum?: number;
    turnin?: string;
    extra_recipe_mappings?: Record<string, number>;
    entry_color?: string;
}

interface PatchouliEntryPage extends Record<string, any> {
    type: string;
}

export interface ConversionProps {
    file: FileWithPath;
    reset: () => void;
}

// See https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/text-formatting/
const defaultMacros: Record<string, string> = {
    "$(obf)": "$(k)",
    "$(bold)": "$(l)",
    "$(strike)": "$(m)",
    "$(italic)": "$(o)",
    "$(italics)": "$(o)",
    "$(list": "$(li",
    "$(reset)": "$()",
    "$(clear)": "$()",
    "$(2br)": "$(br2)",
    "$(p)": "$(br2)",
    "/$": "$()",
    "<br>": "$(br)",
    "$(nocolor)": "$(0)",
    "$(item)": "$(#b0b)",
    "$(thing)": "$(#490)",
};

async function readFile(file: File): Promise<Uint8Array> {
    return new Promise((resolve, reject) => {
        try {
            const reader = new FileReader();
            reader.onloadend = () => {
                try {
                    resolve(new Uint8Array(reader.result as ArrayBuffer));
                } catch (e) {
                    reject(e);
                }
            };
            reader.readAsArrayBuffer(file);
        } catch (e) {
            reject(e);
        }
    });
}

async function extractFile(zipContent: Record<string, ZipItem>, name: string) {
    const f = zipContent[name];
    if (!f) {
        throw 'File not found in zip: ' + name;
    }
    const content = f.read();
    if (content instanceof Uint8Array) {
        return content;
    }
    return await content;
}

async function extractJsonFile(zipContent: Record<string, ZipItem>, name: string) {
    return new Response(await extractFile(zipContent, name)).json();
}

async function loadTranslations(zipContent: Record<string, ZipItem>): Promise<Record<string, Record<string, string>>> {
    const translations: Record<string, Record<string, string>> = {};

    for (const filename of Object.keys(zipContent)) {
        const m = filename.match(/^assets\/[^/]+\/lang\/(\w+)\.json$/);
        if (!m) {
            continue;
        }
        const language = m[1];
        if (!translations[language]) {
            translations[language] = {};
        }
        const strings = await extractJsonFile(zipContent, m[0]);
        for (const [key, value] of Object.entries(strings)) {
            translations[language][key] = String(value);
        }
    }
    return translations;
}

interface UnsortedPage {
    bookNamespace: string;
    bookId: string;
    categoryId: string;
    pageId: string;
    language: string;
    pageData: any;
}

interface UnsortedCategory {
    bookNamespace: string;
    bookId: string;
    categoryId: string;
    language: string;
    categoryData: any;
}

async function getAllCategories(zipContent: ZipContent): Promise<UnsortedCategory[]> {
    const result: UnsortedCategory[] = [];

    for (const filename of Object.keys(zipContent)) {
        const pagePattern = new RegExp(`^assets/([^/]+)/patchouli_books/([^/]+)/([^/]+)/categories/(.*)\.json$`);
        const m = filename.match(pagePattern);
        if (!m) {
            continue;
        }

        const [_, bookNamespace, bookId, language, categoryId] = m;
        const categoryData = await extractJsonFile(zipContent, filename);
        result.push({
            bookNamespace, bookId, language, categoryId, categoryData
        });
    }

    return result;
}

async function getAllPages(zipContent: ZipContent, writeLogLine: (line: string) => void,): Promise<UnsortedPage[]> {
    const result: UnsortedPage[] = [];

    for (const filename of Object.keys(zipContent)) {
        const pagePattern = new RegExp(`^assets/([^/]+)/patchouli_books/([^/]+)/([^/]+)/entries/(.*)\.json$`);
        const m = filename.match(pagePattern);
        if (!m) {
            continue;
        }

        const [_, bookNamespace, bookId, language, pageId] = m;
        const pageData = await extractJsonFile(zipContent, filename);

        const categoryId = pageData.category as string;
        if (!categoryId) {
            writeLogLine(`Page ${filename} has no category`);
            continue;
        }

        result.push({
            bookNamespace, bookId, language, categoryId, pageId, pageData
        });
    }

    return result;
}

async function convertCategory(zipContent: ZipContent,
                               bookNamespace: string,
                               bookId: string,
                               pageId: string,
                               category: PatchouliCategory,
                               writeLogLine: (line: string) => void,
                               outputFiles: Record<string, Uint8Array | string>) {

    const pagePath = `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/${pageId}.md`;
    const lines: string[] = [];

    // Frontmatter
    lines.push("---");
    lines.push("navigation:");
    lines.push("  title: " + JSON.stringify(category.name));
    lines.push("  icon: " + JSON.stringify(category.icon));
    if (category.sortnum) {
        lines.push("  position: " + category.sortnum);
    }
    if (category.parent) {
        lines.push("  parent: " + category.parent + ".md");
    }
    // lines.push("  parent: " + JSON.stringify(page.name));
    lines.push("---");
    lines.push("");

    // Body
    lines.push(`# ${category.name}`)
    lines.push("");
    lines.push("<SubPages />")

    outputFiles[pagePath] = lines.join("\n");
}

function relativePageLink(pageId: string, targetPageId: string): string {
    if (pageId.includes(":")) {
        [, pageId] = pageId.split(":", 2);
    }
    if (targetPageId.includes(":")) {
        [, targetPageId] = targetPageId.split(":", 2);
    }

    // TODO: Resolve the relative link correctly
    return targetPageId + ".md";
}

async function findRecipeResultItem(zipContent: ZipContent,
                                    recipeId: string,
                                    writeLogLine: (line: string) => void): Promise<string | undefined> {

    let [namespace, path] = recipeId.split(":", 2);
    if (!path) {
        path = namespace;
        namespace = 'minecraft';
    }

    const {result} = await extractJsonFile(zipContent, `data/${namespace}/recipe/${path}.json`);

    if (typeof result === "object" && result !== null) {
        const {id} = result;
        if (typeof id === "string") {
            writeLogLine(`Detected result item for recipe ${recipeId} -> ${id}`);
            return id;
        }
    }

    writeLogLine(`Could not detect result item for recipe ${recipeId}`);
    return undefined;

}

async function convertPage(zipContent: ZipContent,
                           macros: Record<string, string>,
                           pages: Record<string, PatchouliEntry>,
                           bookNamespace: string,
                           bookId: string,
                           pageId: string,
                           page: PatchouliEntry,
                           writeLogLine: (line: string) => void,
                           outputFiles: Record<string, Uint8Array | string>) {

    const pagePath = `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/${pageId}.md`;
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
            lines.push(`<a href="${patchouliPage.anchor}"></a>`);
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
                    // TODO Json object support
                    pageItemIds.push(patchouliPage.item);
                    writeLogLine(` Setting page ${pageId} as target for item ${patchouliPage.item}`);
                }
                if (patchouliPage.title) {
                    lines.push("## " + patchouliPage.title);
                    lines.push("");
                }
                // TODO: Json Object support
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

    const body = expandFormatting(lines.join("\n") + "\n", macros, writeLogLine);

    if (pageItemIds.length) {
        frontmatter.push("item_ids:");
        const uniqueItemIds = new Set<string>(pageItemIds);
        for (const id of uniqueItemIds) {
            frontmatter.push("  - " + id);
        }
    }

    outputFiles[pagePath] = "---\n" + frontmatter.join("\n") + "\n---\n\n" + body;
}

async function convertBook(zipContent: ZipContent,
                           bookNamespace: string,
                           bookId: string,
                           unsortedCategories: UnsortedCategory[],
                           unsortedPages: UnsortedPage[],
                           writeLogLine: (line: string) => void,
                           outputFiles: Record<string, Uint8Array | string>) {
    const bookResourceBase = `data/${bookNamespace}/patchouli_books/${bookId}/`;
    writeLogLine(`Book resource base: ${bookResourceBase}`);

    // Read the book.json
    const bookJson = await extractJsonFile(zipContent, bookResourceBase + 'book.json');

    const i18n = bookJson.i18n ?? false;
    writeLogLine(`  i18n: ${i18n}`);

    // For convenience, add pack info
    outputFiles['pack.mcmeta'] = JSON.stringify({
        pack: {
            pack_format: 15,
            supported_formats: {"min_inclusive": 1, "max_inclusive": 999},
            description: "Converted Patchouli Books"
        }
    }, null, 2);

    // Write the data-driven guidebook JSON
    outputFiles[`assets/${bookNamespace}/guideme_guides/${bookId}.json`] = JSON.stringify({}, null, 2);

    // Check that all pages have categories from this book
    for (let {pageId, categoryId} of unsortedPages) {
        if (!categoryId.startsWith(bookNamespace + ":")) {
            writeLogLine(`Page ${pageId} belongs to a category from another book: ${categoryId}`);
        }
    }

    // Find all masters
    const categories = Object.fromEntries(
        unsortedCategories
            .filter(c => c.language === "en_us")
            .map(c => [c.categoryId, c.categoryData])
    );

    for (const categoryId of Object.keys(categories)) {
        const category = categories[categoryId];

        // Enrich with other languages
        const translations: Record<string, any> = {};
        for (let unsortedCategory of unsortedCategories) {
            const lang = unsortedCategory[1];
            if (unsortedCategory[0] === categoryId && lang !== "en_us") {
                translations[lang] = unsortedCategory[2];
            }
        }

        // Find pages
        const pages = Object.fromEntries(unsortedPages
            .filter(p => p.categoryId === `${bookNamespace}:${categoryId}` && p.language === "en_us")
            .map(p => [p.pageId, {
                ...p.pageData,
                translations: Object.fromEntries(
                    unsortedPages
                        .filter(tp => tp.categoryId === `${bookNamespace}:${categoryId}` && tp.pageId === p.pageId && tp.language !== p.language)
                        .map(tp => [tp.language, tp.pageData])
                )
            }]));

        writeLogLine(`Category ${categoryId}`);
        writeLogLine(`  pages: ${Object.keys(pages).length}`);

        await convertCategory(
            zipContent,
            bookNamespace,
            bookId,
            categoryId,
            category,
            writeLogLine,
            outputFiles
        );

        const macros = {...defaultMacros, ...bookJson.macros};

        for (const [pageId, page] of Object.entries(pages)) {
            await convertPage(
                zipContent,
                macros,
                pages,
                bookNamespace,
                bookId,
                pageId,
                page,
                writeLogLine,
                outputFiles
            )
        }
    }
}

async function convert(file: FileWithPath, writeLogLine: (line: string) => void): Promise<Record<string, Uint8Array | string>> {
    writeLogLine(`Loading ${file.name}...`);

    const zipItems = unzip(await readFile(file));
    const zipContent = Object.fromEntries(zipItems.map(item => [item.filename, item]));

    // Find potential Patchouli books in the resource-location format
    const books = zipItems
        .map(({filename}) => {
            const m = filename.match(/^data\/([^/]+)\/patchouli_books\/([^/]+)\/book\.json$/);
            if (m) {
                return [m[1], m[2]];
            }
            return undefined;
        })
        .filter(m => m !== undefined);

    if (books.length === 0) {
        throw 'No books found';
    }

    // Load en_us language files
    const translations = await loadTranslations(zipContent);
    writeLogLine(`Found translation files for ${Object.keys(translations).length} languages`);

    // Load all categories
    writeLogLine("Loading all categories...");
    const unsortedCategories = await getAllCategories(zipContent);
    writeLogLine(`  ${unsortedCategories.length} categories found...`);
    writeLogLine("Loading all pages...");
    const unsortedPages = await getAllPages(zipContent, writeLogLine);
    writeLogLine(`  ${unsortedPages.length} pages found...`);

    const outputFiles: Record<string, Uint8Array | string> = {};
    for (let [bookNamespace, bookId] of books) {
        writeLogLine(`Found book ${bookNamespace}:${bookId}`);

        const bookCategories = unsortedCategories.filter(c => c.bookNamespace === bookNamespace && c.bookId === bookId);
        const bookPages = unsortedPages.filter(p => p.bookNamespace === bookNamespace && p.bookId === bookId);

        await convertBook(zipContent, bookNamespace, bookId, bookCategories, bookPages, writeLogLine, outputFiles);
    }
    return outputFiles;
}

function Conversion({file, reset}: ConversionProps) {
    const [error, setError] = useState<any | null>(null);
    const [logLines, setLogLines] = useState<string[]>([]);
    const [outputFiles, setOutputFiles] = useState<Record<string, Uint8Array | string> | null>(null);

    useEffect(() => {
        // Begin by resetting
        setLogLines([]);
        setOutputFiles(null);
        setError(null);
        let canceled = false;

        convert(file, line => setLogLines(lines => lines.concat([line])))
            .then(files => {
                if (!canceled) {
                    setOutputFiles(files);
                }
            })
            .catch(err => {
                if (!canceled) {
                    setError(err);
                }
            });

        return () => {
            canceled = true;
        }
    }, [file]);

    const conversionLog = logLines.length ? <div>
        <strong>Conversion Log</strong>
        <pre>{logLines.join("\n")}</pre>
    </div> : null;

    if (error) {
        return <>
            <div className="alert alert--danger" role="alert">
                <button aria-label="Close" className="clean-btn close" type="button" onClick={reset}>
                    <span aria-hidden="true">&times;</span>
                </button>
                <strong>Conversion Error</strong><br/>
                {error.toString()}
                {error.stack ? <pre>{error.stack.toString()}</pre> : null}
            </div>
            {conversionLog}
        </>;
    }

    return (<>
            {outputFiles && <DownloadZipFile files={outputFiles}/>}
            {conversionLog}
            {outputFiles && <OutputFilesBrowser files={outputFiles}/>}
        </>
    );
}

export default Conversion;