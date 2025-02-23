import {FileWithPath} from "react-dropzone";
import {useEffect, useState} from "react";
import {unzip} from 'but-unzip';
import OutputFilesBrowser from "./OutputFilesBrowser";
import DownloadZipFile from "@site/src/components/patchouli/DownloadZipFile";
import {extractJsonFile, loadTranslations, readFile} from "@site/src/components/patchouli/util";
import {ZipContent} from "@site/src/components/patchouli/types";
import {convertBook, UnsortedCategory, UnsortedPage} from "@site/src/components/patchouli/convertBook";

export interface ConversionProps {
    file: FileWithPath;
    reset: () => void;
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

type ConversionResult = {
    namespaces: string[];
    outputFiles: Record<string, Uint8Array | string>;
}

async function convert(file: FileWithPath, writeLogLine: (line: string) => void): Promise<ConversionResult> {
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

    const namespaces: string[] = [];
    const outputFiles: Record<string, Uint8Array | string> = {};
    for (let [bookNamespace, bookId] of books) {
        writeLogLine(`Found book ${bookNamespace}:${bookId}`);

        const bookCategories = unsortedCategories.filter(c => c.bookNamespace === bookNamespace && c.bookId === bookId);
        const bookPages = unsortedPages.filter(p => p.bookNamespace === bookNamespace && p.bookId === bookId);

        await convertBook(zipContent, bookNamespace, bookId, bookCategories, bookPages, translations, writeLogLine, outputFiles);
        if (!namespaces.includes(bookNamespace)) {
            namespaces.push(bookNamespace);
        }
    }

    return {namespaces, outputFiles};
}

function Conversion({file, reset}: ConversionProps) {
    const [error, setError] = useState<any | null>(null);
    const [logLines, setLogLines] = useState<string[]>([]);
    const [namespaces, setNamespaces] = useState<string[]>([]);
    const [outputFiles, setOutputFiles] = useState<Record<string, Uint8Array | string> | null>(null);

    useEffect(() => {
        // Begin by resetting
        setLogLines([]);
        setNamespaces([]);
        setOutputFiles(null);
        setError(null);
        let canceled = false;

        convert(file, line => setLogLines(lines => lines.concat([line])))
            .then(({namespaces, outputFiles}) => {
                if (!canceled) {
                    setNamespaces(namespaces);
                    setOutputFiles(outputFiles);
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
            {outputFiles && <DownloadZipFile namespaces={namespaces} files={outputFiles}/>}
            {conversionLog}
            {outputFiles && <OutputFilesBrowser files={outputFiles}/>}
        </>
    );
}

export default Conversion;