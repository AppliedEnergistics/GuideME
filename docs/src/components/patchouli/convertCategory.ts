import {PatchouliCategory, ZipContent} from "@site/src/components/patchouli/types";
import {expandFormatting} from "@site/src/components/patchouli/formatting";

export async function convertCategory(zipContent: ZipContent,
                                      macros: Record<string, string>,
                                      bookNamespace: string,
                                      bookId: string,
                                      categoryId: string,
                                      category: PatchouliCategory,
                                      language: string | undefined,
                                      translateText: (text: string) => string,
                                      writeLogLine: (line: string) => void,
                                      outputFiles: Record<string, Uint8Array | string>) {

    const pagePath = `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/${categoryId}.md`;
    const translatedPagePath = language ?
        `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/_${language}/${categoryId}.md`
        : pagePath;
    const lines: string[] = [];

    // Frontmatter
    lines.push("---");
    lines.push("navigation:");
    lines.push("  title: " + JSON.stringify(translateText(category.name)));
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
    lines.push(`# ${translateText(category.name)}`)
    lines.push("");
    const description = translateText(category.description);
    if (description) {
        lines.push(expandFormatting(categoryId, description, macros, writeLogLine));
        lines.push("");
    }
    lines.push("<SubPages />")

    outputFiles[translatedPagePath] = lines.join("\n");
}
