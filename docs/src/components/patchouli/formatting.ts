const simpleFormatTags = {
    l: ['**', '**'],
    m: ['~~', '~~'],
    n: ['__', '__'],
    o: ['*', '*'],
    k: ['<Obf>', '</Obf>']
}

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
    '$(nocolor)': '$(0)',
    '$(item)': '$(#b0b)',
    '$(thing)': '$(#490)',
};

export function expandFormatting(body: string, macros: Record<string, string>, writeLogLine: (line: string) => void): string {
    let result = '';
    let formatStack: string[] = [];  // Stack to track current formatting state

    function endLink(linkTarget: string) {
        if (linkTarget.startsWith("http://") || linkTarget.startsWith("https://")) {
            result += "](" + linkTarget + ")";
        } else {
            result += "](" + linkTarget + ".md)";
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
                result += `//color#${tag.slice(1)}`;
            } else if (tag === 'k') {
                result += "$(k)";
                formatStack.push(tag);
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
                        result += "$(/" + tag + ")";
                    }
                }
            } else if (tag === "/l") {
                // Close link
                const lastEntry = formatStack[formatStack.length - 1];
                if (lastEntry?.startsWith('l:')) {
                    endLink(lastEntry.slice(2));
                }
            } else {
                // Any unrecognized formatting tag, output as is
                result += `$(${tag})`;
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
