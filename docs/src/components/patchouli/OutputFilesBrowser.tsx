import {ChangeEvent, useCallback, useState} from "react";

export interface OutputFilesBrowserProps {
    files: Record<string, Uint8Array | string>;
}

function OutputFilesBrowser({files}: OutputFilesBrowserProps) {
    const [fileContent, setFileContent] = useState<string>(null);

    const selectFile = useCallback((e: ChangeEvent<HTMLSelectElement>) => {
        let file = files[e.target.value];
        if (!file) {
            setFileContent(null);
            return;
        }
        if (typeof file === "string") {
            setFileContent(file);
        } else {
            setFileContent(`Binary File (${file.length})`);
        }
    }, [files]);

    return (
        <section>
            <select onChange={selectFile}>
                {Object.keys(files).sort().map((filename) => (
                    <option key={filename} value={filename}>{filename}</option>))}
            </select>
            {fileContent && <pre>{fileContent}</pre>}
        </section>
    );
}

export default OutputFilesBrowser;