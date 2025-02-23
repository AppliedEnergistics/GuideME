import {ZipItem} from "but-unzip";
import {ZipContent} from "@site/src/components/patchouli/types";

export async function readFile(file: File): Promise<Uint8Array> {
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

export async function extractFile(zipContent: Record<string, ZipItem>, name: string) {
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

export async function extractJsonFile(zipContent: Record<string, ZipItem>, name: string) {
    return new Response(await extractFile(zipContent, name)).json();
}

export function relativePageLink(pageId: string, targetPageId: string): string {
    if (pageId.includes(":")) {
        [, pageId] = pageId.split(":", 2);
    }
    if (targetPageId.includes(":")) {
        [, targetPageId] = targetPageId.split(":", 2);
    }

    let pageIdParts = pageId.split("/");
    let targetPageParts = targetPageId.split("/");
    let commonPrefix = 0;
    for (let i = 0; i < Math.min(pageIdParts.length, targetPageParts.length); i++) {
        if (pageIdParts[i] === targetPageParts[i]) {
            commonPrefix++;
        } else {
            break;
        }
    }

    if (commonPrefix >= pageIdParts.length - 1) {
        return "./" + targetPageParts.pop() + ".md";
    }

    return "../".repeat(pageIdParts.length - 1 - commonPrefix) + targetPageParts.pop() + ".md";
}

export async function findRecipeResultItem(zipContent: ZipContent,
                                           recipeId: string,
                                           writeLogLine: (line: string) => void): Promise<string | undefined> {

    let [namespace, path] = recipeId.split(":", 2);
    if (!path) {
        path = namespace;
        namespace = 'minecraft';
    }

    let recipeJson: any;

    try {
        recipeJson = await extractJsonFile(zipContent, `data/${namespace}/recipe/${path}.json`);
    } catch (e) {
        // Try the pre-1.20.1 fallback
        try {
            recipeJson = await extractJsonFile(zipContent, `data/${namespace}/recipes/${path}.json`);
        } catch (e) {
            writeLogLine(`Could not find recipe ${recipeId} in mod jar. Cannot determine result item.`);
            return undefined;
        }
    }

    const {result} = recipeJson;

    if (typeof result === "object" && result !== null) {
        const {id, item} = result;
        if (typeof id === "string") {
            writeLogLine(`Detected result item for recipe ${recipeId} -> ${id}`);
            return id;
        } else if (typeof item === "string") {
            // item is the 1.20.1 style
            writeLogLine(`Detected result item for recipe ${recipeId} -> ${item}`);
            return item;
        }
    }

    writeLogLine(`Could not detect result item for recipe ${recipeId}`);
    return undefined;

}

export function splitIdAndData(id: string): [string, string | undefined] {
    if (id.endsWith("}")) {
        const startOfData = id.indexOf('{');
        return [
            id.substring(0, startOfData),
            id.substring(startOfData + 1, id.length - 1),
        ];
    }
    return [id, undefined];
}

export async function loadTranslations(zipContent: Record<string, ZipItem>): Promise<Record<string, Record<string, string>>> {
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
