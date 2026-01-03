import esbuild from "esbuild";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import url from "node:url";

const dirname = path.dirname(url.fileURLToPath(import.meta.url));

async function build() {
    // Clean dist directory
    const distDir = path.join(dirname, 'dist');
    if (fs.existsSync(distDir)) {
        fs.rmSync(distDir, {recursive: true});
    }
    fs.mkdirSync(distDir, {recursive: true});
    fs.mkdirSync(path.join(distDir, 'assets'), {recursive: true});
    fs.mkdirSync(path.join(distDir, 'templates'), {recursive: true});

    // Bundle JavaScript
    const buildResult = await esbuild.build({
        entryPoints: ['src/index.ts'],
        bundle: true,
        splitting: true,
        outdir: 'dist/assets/',
        entryNames: 'bundle-[hash]',
        minify: true,
        metafile: true,
        format: "esm",
        loader: {
            '.woff': 'file',
            '.woff2': 'file',
            '.png': 'file',
            '.jpg': 'file',
            '.jpeg': 'file',
            '.gif': 'file',
            '.svg': 'file',
            '.webp': 'file',
        },
    });

    // Extract output filenames
    const jsOutputs = Object.keys(buildResult.metafile.outputs).filter(f => f.endsWith('.js'));
    const cssOutputs = Object.keys(buildResult.metafile.outputs).filter(f => f.endsWith('.css'));

    const jsFile = path.basename(jsOutputs[0]);
    if (!jsFile) {
        throw new Error("Missing expected JS output: " + JSON.stringify(buildResult.metafile.outputs));
    }
    const cssFile = path.basename(cssOutputs[0]);
    if (!cssFile) {
        throw new Error("Missing expected CSS output: " + JSON.stringify(buildResult.metafile.outputs));
    }

    // Read and process HTML
    let htmlTemplate = fs.readFileSync('src/layout.html', 'utf-8');

    // Find all desired assets to copy
    let htmlOutput = htmlTemplate.replaceAll(/\{\{asset:([^}]+)}}/g, (substring, assetPath) => {
        const sourceAssetPath = path.join(dirname, assetPath);

        // Read the asset content
        const assetContent = fs.readFileSync(sourceAssetPath);

        // Hash the content for cache busting
        const hash = crypto.createHash('sha256').update(assetContent).digest('hex').substring(0, 12);

        // Parse the path to insert hash before extension
        const assetDir = path.dirname(assetPath);
        const assetFilename = path.basename(assetPath);
        const dotIndex = assetFilename.lastIndexOf('.');

        let cacheBustedFilename;
        if (dotIndex === -1) {
            cacheBustedFilename = `${assetFilename}.${hash}`;
        } else {
            const name = assetFilename.substring(0, dotIndex);
            const ext = assetFilename.substring(dotIndex);
            cacheBustedFilename = `${name}.${hash}${ext}`;
        }

        // Construct output path maintaining relative directory structure
        const outputPath = path.join(distDir, assetDir, cacheBustedFilename);

        // Ensure output directory exists
        fs.mkdirSync(path.dirname(outputPath), {recursive: true});

        // Copy the asset to output directory
        fs.writeFileSync(outputPath, assetContent);

        // Return the relative path from output root
        return path.join(assetDir, cacheBustedFilename).replace(/\\/g, '/');
    });

    htmlOutput = htmlOutput
        .replace(/\{\{JS_BUNDLE\}\}/g, 'assets/' + jsFile)
        .replace(/\{\{CSS_BUNDLE\}\}/g, 'assets/' + cssFile);

    // Write processed HTML
    fs.writeFileSync('dist/templates/layout.html', htmlOutput);

    // Write full assets file list
    const assetList = fs.readdirSync('dist/assets', {recursive: true}).map(p => path.join('assets', p).replace("\\", "/"));
    fs.writeFileSync('dist/index.txt', assetList.join("\n"));
}

build().catch(err => {
    console.error(err);
    process.exit(1);
});
