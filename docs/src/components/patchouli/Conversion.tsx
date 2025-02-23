import {FileWithPath} from "react-dropzone";
import {useEffect, useState} from "react";
import {unzip} from 'but-unzip';
import OutputFilesBrowser from "./OutputFilesBrowser";
import DownloadZipFile from "@site/src/components/patchouli/DownloadZipFile";
import {extractJsonFile, loadTranslations, readFile} from "@site/src/components/patchouli/util";
import {PatchouliEntry, ZipContent} from "@site/src/components/patchouli/types";
import {convertPage} from "@site/src/components/patchouli/convertPage";
import {convertCategory} from "@site/src/components/patchouli/convertCategory";

export interface ConversionProps {
    file: FileWithPath;
    reset: () => void;
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
            const lang = unsortedCategory.language;
            if (unsortedCategory.categoryId === categoryId && lang !== "en_us") {
                translations[lang] = unsortedCategory.categoryData;
            }
        }
        console.log(translations);

        // Find pages
        const pages = Object.fromEntries(unsortedPages
            .filter(p => p.categoryId === `${bookNamespace}:${categoryId}` && p.language === "en_us")
            .map(p => {
                let pageId = p.pageId;
                if (pageId === categoryId) {
                    writeLogLine(`Renaming ${pageId} to ${pageId}_page due to clash with category ${categoryId}`);
                    pageId = pageId + "_page";
                }
                return [pageId, {
                    ...p.pageData as PatchouliEntry,
                    translations: Object.fromEntries(
                        unsortedPages
                            .filter(tp => tp.categoryId === `${bookNamespace}:${categoryId}` && tp.pageId === p.pageId && tp.language !== p.language)
                            .map(tp => [tp.language, tp.pageData as PatchouliEntry])
                    )
                }];
            }));

        writeLogLine(`Category ${categoryId}`);
        writeLogLine(`  pages: ${Object.keys(pages).length}`);
        writeLogLine(`  translations: ${Object.keys(translations)}`);

        await convertCategory(
            zipContent,
            bookNamespace,
            bookId,
            categoryId,
            category,
            undefined,
            writeLogLine,
            outputFiles
        );
        for (const [language, translatedCategory] of Object.entries(translations)) {
            await convertCategory(
                zipContent,
                bookNamespace,
                bookId,
                categoryId,
                translatedCategory,
                language,
                writeLogLine,
                outputFiles
            );
        }

        for (const [pageId, page] of Object.entries(pages)) {
            await convertPage(
                zipContent,
                bookJson.macros,
                pages,
                bookNamespace,
                bookId,
                pageId,
                page,
                undefined,
                writeLogLine,
                outputFiles
            )

            for (let [language, translatedPage] of Object.entries(page.translations)) {
                await convertPage(
                    zipContent,
                    bookJson.macros,
                    pages,
                    bookNamespace,
                    bookId,
                    pageId,
                    translatedPage,
                    language,
                    writeLogLine,
                    outputFiles
                )
            }
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