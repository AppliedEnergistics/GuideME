import {MouseEvent} from "react";
import {downloadZip, InputWithSizeMeta} from "client-zip";

export interface DownloadZipFileProps {
    files: Record<string, Uint8Array | string>;
}

async function buildZip(files: Record<string, Uint8Array | string>): Promise<Blob> {
    const textEncoder = new TextEncoder();
    let zipInput: InputWithSizeMeta[] = [];

    for (const [path, content] of Object.entries(files)) {
        let input = typeof content === "string" ? textEncoder.encode(content) : content;

        zipInput.push({
            name: path,
            input
        });
    }

    return downloadZip(zipInput).blob();
}

function DownloadZipFile({files}: DownloadZipFileProps) {
    const downloadFiles = (e: MouseEvent<HTMLButtonElement>) => {
        e.preventDefault();

        buildZip(files)
            .then(zip => {
                // make and click a temporary link to download the Blob
                const link = document.createElement("a")
                link.href = URL.createObjectURL(zip);
                link.download = "converted_patchouli_books.zip";
                link.click();
                link.remove();
                URL.revokeObjectURL(link.href);
            })
            .catch(err => {
                alert("Failed to create ZIP: " + err);
            });
    };

    return (
        <p>
            <button onClick={downloadFiles} className="button button--primary">
                Download Zip File
            </button>
        </p>
    );
}

export default DownloadZipFile;