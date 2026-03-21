import {mkdir, writeFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {dirname} from 'node:path';
import gm from "@strudel/soundfonts/gm.mjs";

async function saveDataAsJson(data, path) {
    try {
        // 1. Ensure the directory exists
        await mkdir(dirname(path), {recursive: true});

        const content = JSON.stringify(data, null, 2);
        await writeFile(path, content, 'utf8');
        console.log('File written successfully!');
    } catch (err) {
        console.error('Error writing file:', err);
    }
}

async function loadWebAudioFont(baseUrl, name) {
    const url = baseUrl + name + ".js";

    console.log(`Fetching ${url}...`);
    const response = await fetch(url)

    if (!response.ok) {
        console.error("Error fetching", url, response.status);
        return null
    }

    const text = await response.text()

    const code = `
        ${text};
        
        return _tone_${name};
    `

    // console.log("Evaluating code...", code);
    // Evaluate safely
    try {
        return (new Function(code))();
    } catch (e) {
        console.error("Error evaluating code", url, e);
        return null;
    }
}

function cleanVariantName(name) {
    // ^\d+_ matches digits and the first underscore at the start
    // _sf2_file$ matches that specific suffix at the end
    return name.replace(/^\d+_/, '').replace(/_sf2_file$/, '');
}

const gmOutputDir = "./tmp/gm/"

async function extractGmSoundFonts() {
    await saveDataAsJson(gm, gmOutputDir + "original.json")

    const gmBaseUrl = "https://felixroos.github.io/webaudiofontdata/sound/"

    const gmMapped = {}

    Object.entries(gm).forEach(([key, variants]) => {
        console.log("Font", key, "Num variants", variants.length)

        gmMapped[key] = variants
            .filter(variant => variant.trim() !== '')
            .map(variant => {
                const variantName = cleanVariantName(variant)

                return {
                    name: variantName,
                    source: {
                        baseUrl: gmBaseUrl,
                        file: variant,
                    },
                    file: key + "/" + variant + ".json",
                }
            })
    })

    await saveDataAsJson(gmMapped, gmOutputDir + "index.json")

    return gmMapped
}

async function gmDownloadAndConvert(gmMapped) {

    for (const [_, variants] of Object.entries(gmMapped)) {
        for (const variant of variants) {
            const outputFile = gmOutputDir + "/" + variant.file

            // Only download if the file doesn't exist yet
            if (existsSync(outputFile)) {
                console.log("Skipping", variant.name, outputFile, "because it already exists")
            } else {
                let font = await loadWebAudioFont(variant.source.baseUrl, variant.source.file)

                if (font) {
                    await saveDataAsJson(font, outputFile)
                }
            }
        }
    }
}

const gmMapped = await extractGmSoundFonts()

await gmDownloadAndConvert(gmMapped)


// const baseUrl = "https://felixroos.github.io/webaudiofontdata/sound/"
// const name = "0130_JCLive_sf2_file"
//
// const font = await loadWebAudioFont(baseUrl, name)

// console.log("--------------------------------------------------------------")
// console.log("--------------------------------------------------------------")
// console.log("--------------------------------------------------------------")
// console.log("--------------------------------------------------------------")
// console.log("--------------------------------------------------------------")
// console.log("--------------------------------------------------------------")
//
// console.log(
//     JSON.stringify(font, null, 2)
// )
