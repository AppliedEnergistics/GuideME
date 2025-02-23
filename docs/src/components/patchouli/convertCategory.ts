import {PatchouliCategory, ZipContent} from "@site/src/components/patchouli/types";

export async function convertCategory(zipContent: ZipContent,
                               bookNamespace: string,
                               bookId: string,
                               pageId: string,
                               category: PatchouliCategory,
                               language: string | undefined,
                               writeLogLine: (line: string) => void,
                               outputFiles: Record<string, Uint8Array | string>) {

    const pagePath = `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/${pageId}.md`;
    const translatedPagePath = language ?
        `assets/${bookNamespace}/guides/${bookNamespace}/${bookId}/_${language}/${pageId}.md`
        : pagePath;
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

    outputFiles[translatedPagePath] = lines.join("\n");
}
