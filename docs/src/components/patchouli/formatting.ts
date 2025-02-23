import {relativePageLink} from "@site/src/components/patchouli/util";

const simpleFormatTags = {
    l: ['**', '**'],
    m: ['~~', '~~'],
    n: ['__', '__'],
    o: ['*', '*'],
    k: ['<Obf>', '</Obf>'],
    // These are actually macros for color in Patchouli, but we want to translate this to emphasis
    // "$(thing)": "$(#490)",
    thing: ['*', '*'],
    // "$(item)": "$(#b0b)",
    item: ['*', '*'],
    '0': ['<Color id="black">', '</Color>'],
    '1': ['<Color id="dark_blue">', '</Color>'],
    '2': ['<Color id="dark_green">', '</Color>'],
    '3': ['<Color id="dark_aqua">', '</Color>'],
    '4': ['<Color id="dark_red">', '</Color>'],
    '5': ['<Color id="dark_purple">', '</Color>'],
    '6': ['<Color id="gold">', '</Color>'],
    '7': ['<Color id="gray">', '</Color>'],
    '8': ['<Color id="dark_gray">', '</Color>'],
    '9': ['<Color id="blue">', '</Color>'],
    'a': ['<Color id="green">', '</Color>'],
    'b': ['<Color id="aqua">', '</Color>'],
    'c': ['<Color id="red">', '</Color>'],
    'd': ['<Color id="light_purple">', '</Color>'],
    'e': ['<Color id="yellow">', '</Color>'],
    'f': ['<Color id="white">', '</Color>'],
}

// See https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/text-formatting/
const defaultMacros = {
    '$(obf)': '$(k)',
    '$(bold)': '$(l)',
    '$(strike)': '$(m)',
    '$(italic)': '$(o)',
    '$(italics)': '$(o)',
    '$(list': '$(li',
    '$(reset)': '$()',
    '$(clear)': '$()',
    '$(2br)': '$(br2)',
    '$(p)': '$(br2)',
    '/$': '$()',
    '<br>': '$(br)',
    '$(nocolor)': '$(0)'
};

export function expandFormatting(currentPageId: string, body: string, macros: Record<string, string>, writeLogLine: (line: string) => void): string {
    let result = '';
    let formatStack: string[] = [];  // Stack to track current formatting state

    function endLink(linkTarget: string) {
        if (linkTarget.startsWith("http://") || linkTarget.startsWith("https://")) {
            result += "](" + linkTarget + ")";
        } else {
            if (linkTarget.includes("#")) {
                const [targetPage, fragment] = linkTarget.split('#', 2);
                if (targetPage === currentPageId) {
                    linkTarget = "#" + fragment;
                } else {
                    linkTarget = relativePageLink(currentPageId, targetPage) + "#" + fragment;
                }
            } else {
                linkTarget = relativePageLink(currentPageId, linkTarget)
            }

            result += "](" + linkTarget + ")";
        }
    }

    macros = {...defaultMacros, ...macros};
    for (const [from, to] of Object.entries(macros)) {
        body = body.replaceAll(from, to);
    }

    let i = 0;
    while (i < body.length) {
        if (body[i] === '$' && body[i + 1] === '(') {
            let tagStart = i + 2;
            let tagEnd = body.indexOf(')', tagStart);

            if (tagEnd === -1) {
                result += body[i];
                i++;
                continue;
            }

            let tag = body.slice(tagStart, tagEnd);

            // Handle specific formatting tags
            if (tag === 'br') {
                result += '\n';
            } else if (tag === 'br2') {
                result += '\n\n';
            } else if (tag.startsWith('li')) {
                let level = parseInt(tag.slice(2)) || 0;
                result += '\n' + '  '.repeat(level) + '- ';
            } else if (tag.match(/^#([0-9a-fA-F]{3,6})$/)) {
                // Handling color codes, we add a placeholder for now
                result += `<Color hex="${tag}">`;
                formatStack.push('</Color>');
            } else if (tag in simpleFormatTags) {
                result += simpleFormatTags[tag][0];
                formatStack.push(tag);
            } else if (tag.startsWith('l:')) {
                result += "[";
                formatStack.push(tag);
            } else if (tag === "") {
                // Close all
                while (formatStack.length > 0) {
                    const tag = formatStack.pop();
                    if (tag.startsWith("l:")) {
                        endLink(tag.slice(2));
                    } else if (tag in simpleFormatTags) {
                        result += simpleFormatTags[tag][1];
                    } else {
                        result += tag;
                    }
                }
            } else if (tag === "/l") {
                // Close link
                const lastEntry = formatStack.pop();
                if (lastEntry?.startsWith('l:')) {
                    endLink(lastEntry.slice(2));
                }
            } else {
                // Any unrecognized formatting tag, output as is
                result += `$(${tag})`;
                formatStack.push("$(/" + tag + ")");
                writeLogLine(`Unrecognized formatting tag ${tag}`);
            }

            i = tagEnd + 1;  // Move past the closing ')'
        } else {
            // Just append normal characters to the result
            result += body[i];
            i++;
        }
    }

    return result;
}
