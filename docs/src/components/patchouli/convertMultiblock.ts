export function convertMultiblock(multiblock: any, writeLogLine: (line: string) => void): string[] {
    const lines: string[] = [];
    lines.push("<GameScene interactive={true} zoom={2}>");

    const {mapping, pattern} = multiblock as {
        mapping: Record<string, string>;
        pattern: string[][];
    };

    for (let y = 0; y < pattern.length; y++) {
        const row = pattern[y];
        for (let z = 0; z < row.length; z++) {
            const col = row[z];
            for (let x = 0; x < col.length; x++) {
                const patternCh = col[x];
                if (patternCh === ' ') {
                    continue;
                }

                let mapped = mapping[patternCh];
                if (!mapped) {
                    if (patternCh !== '0') {
                        writeLogLine('Unknown mapping character in multiblock ' + patternCh);
                    }
                    continue;
                }

                // Convert the block property list to tag properties
                let startOfProps = mapped.indexOf('[');
                let propertiesAttr = '';
                if (startOfProps !== -1 && mapped.endsWith("]")) {
                    const properties = mapped.substring(startOfProps + 1, mapped.length - 1).split(",");
                    mapped = mapped.substring(0, startOfProps);
                    propertiesAttr = properties.map(p => {
                        let [key, value] = p.split("=", 2);
                        if (value.startsWith('"') && value.endsWith('"')
                            || value.startsWith('\'') && value.endsWith('\'')) {
                            value = value.substring(1, value.length - 1);
                        }

                        return `p:${key}="${value}"`;
                    }).join(" ");
                }

                lines.push(`  <Block x="${x}" y="${y}" z="${z}" id="${mapped}"${propertiesAttr} />`);
            }
        }
    }

    lines.push("</GameScene>");
    return lines;
}