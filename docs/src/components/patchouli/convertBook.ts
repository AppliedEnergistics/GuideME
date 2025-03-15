import {PatchouliCategory, PatchouliEntry, ZipContent} from "@site/src/components/patchouli/types";
import {extractJsonFile} from "@site/src/components/patchouli/util";
import {convertCategory} from "@site/src/components/patchouli/convertCategory";
import {convertPage} from "@site/src/components/patchouli/convertPage";

export interface UnsortedPage {
    bookNamespace: string;
    bookId: string;
    categoryId: string;
    pageId: string;
    language: string;
    pageData: any;
}

export interface UnsortedCategory {
    bookNamespace: string;
    bookId: string;
    categoryId: string;
    language: string;
    categoryData: any;
}

export async function convertBook(zipContent: ZipContent,
                                  bookNamespace: string,
                                  bookId: string,
                                  unsortedCategories: UnsortedCategory[],
                                  unsortedPages: UnsortedPage[],
                                  translations: Record<string, Record<string, string>>,
                                  writeLogLine: (line: string) => void,
                                  outputFiles: Record<string, Uint8Array | string>) {
    const bookResourceBase = `data/${bookNamespace}/patchouli_books/${bookId}/`;
    writeLogLine(`Book resource base: ${bookResourceBase}`);

    // Read the book.json
    const bookJson = await extractJsonFile(zipContent, bookResourceBase + 'book.json');

    const i18n: boolean = bookJson.i18n ?? false;
    writeLogLine(`  i18n: ${i18n}`);

    const translate = (language: string | undefined, text: string): string => {
        if (!i18n) {
            return text;
        }

        if (!text) {
            return text;
        }

        language = language ?? "en_us";

        const translatedText = translations[language]?.[text];
        if (translatedText) {
            return translatedText;
        }

        writeLogLine(`Missing translation of ${text} for language ${language}`);
    };

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
        const categoryTranslations: Record<string, any> = {};
        for (let unsortedCategory of unsortedCategories) {
            const lang = unsortedCategory.language;
            if (unsortedCategory.categoryId === categoryId && lang !== "en_us") {
                categoryTranslations[lang] = unsortedCategory.categoryData;
            }
        }

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
        writeLogLine(`  translations: ${Object.keys(categoryTranslations)}`);

        const convertCategoryTranslation = async (language: string | undefined, categoryData: PatchouliCategory) => {
            await convertCategory(
                zipContent,
                bookJson.macros,
                bookNamespace,
                bookId,
                categoryId,
                categoryData,
                language,
                text => translate(language, text),
                writeLogLine,
                outputFiles
            );
        }

        await convertCategoryTranslation(undefined, category);
        if (i18n) {
            for (const language of Object.keys(translations)) {
                await convertCategoryTranslation(language, category);
            }
        }
        for (const [language, translatedCategory] of Object.entries(categoryTranslations)) {
            await convertCategoryTranslation(language, translatedCategory);
        }

        for (const [pageId, page] of Object.entries(pages)) {
            const convertPageTranslation = async (language: string | undefined, pageData: PatchouliEntry) => {
                await convertPage(
                    zipContent,
                    bookJson.macros,
                    pages,
                    bookNamespace,
                    bookId,
                    pageId,
                    pageData,
                    language,
                    text => translate(language, text),
                    writeLogLine,
                    outputFiles
                );
            }

            await convertPageTranslation(undefined, page);
            if (i18n) {
                for (const language of Object.keys(translations)) {
                    await convertPageTranslation(language, page);
                }
            }
            for (let [language, translatedPage] of Object.entries(page.translations)) {
                await convertPageTranslation(language, translatedPage);
            }
        }
    }
}
