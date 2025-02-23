import Conversion from "@site/src/components/patchouli/Conversion";
import {FileWithPath, useDropzone} from "react-dropzone";
import {useCallback, useMemo, useState} from "react";
import css from './PatchouliConverter.module.css';
import Video from "@site/src/components/Video";

const rootClasses = 'card ' + css.dragInactive;
const rootClassesDragActive = 'card ' + css.dragActive;

function PatchouliConverter() {
    const [file, setFile] = useState<FileWithPath | null>(null);
    const {getRootProps, getInputProps, open, isDragActive} = useDropzone({
        accept: {
            "application/*": [".jar", ".zip"],
        },
        maxFiles: 1,
        onDrop: (acceptedFiles) => {
            if (acceptedFiles[0]) {
                setFile(acceptedFiles[0]);
            }
        }
    });
    const reset = useCallback(() => {
        setFile(null);
    }, []);

    return (
        <>
            <div className={isDragActive ? rootClassesDragActive : rootClasses} {...getRootProps()}>
                <div className="card__body">
                    <input {...getInputProps()} />
                    <p>Drop a mod jar or resource pack containing a Patchouli guidebook here.</p>
                </div>
                <div className="card__footer">
                    <button className="button button--secondary" onClick={open}>Select Jar or Zip File</button>
                </div>
            </div>
            <p/>
            {!file && <p>You can see a demonstration of the conversion process in the following video: <Video src="patchouli-conversion.mp4" /></p>}
            {file && <Conversion file={file} reset={reset}/>}
        </>
    );
}

export default PatchouliConverter;
