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
    const entryPoint = 'src/index.ts';
    const buildResult = await esbuild.build({
        entryPoints: [entryPoint],
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

    // Find the bundles of the entry point. Code splitting creates other JS outputs, i.e. for chunks shared by dynamic
    // imports, which must not be loaded directly.
    const outputs = buildResult.metafile.outputs;
    const jsOutput = Object.keys(outputs).find(f => outputs[f].entryPoint === entryPoint);
    if (!jsOutput) {
        throw new Error(`Missing JS output for ${entryPoint}: ${JSON.stringify(outputs)}`);
    }
    // The CSS imported by the entry point is bundled separately
    const cssOutput = outputs[jsOutput].cssBundle;
    if (!cssOutput) {
        throw new Error(`Missing CSS output for ${entryPoint}: ${JSON.stringify(outputs)}`);
    }
    const jsFile = path.basename(jsOutput);
    const cssFile = path.basename(cssOutput);

    // Read and process the HTML templates
    for (const template of ['layout.html', 'versions.html']) {
        let htmlTemplate = fs.readFileSync(path.join('src', template), 'utf-8');

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

        // Write processed HTML
        fs.writeFileSync(path.join('dist/templates', template), htmlOutput);
    }

    // Write the hashed names of the bundles, which GuideME adds to the templates with placeholders
    fs.writeFileSync(path.join('dist/templates', 'bundles.properties'),
        `stylesheet=assets/${cssFile}\nscript=assets/${jsFile}\n`);

    // Write full assets file list
    // Sort the list, since the order of directory listings depends on the OS and file system
    const assetList = fs.readdirSync('dist/assets', {recursive: true})
        .map(p => path.join('assets', p).replaceAll("\\", "/"))
        .sort();
    fs.writeFileSync('dist/index.txt', assetList.join("\n"));
}

build().catch(err => {
    console.error(err);
    process.exit(1);
});
