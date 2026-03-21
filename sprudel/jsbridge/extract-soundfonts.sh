#!/bin/bash
cd "$(dirname "$0")" || exit

# Bundle the extraction script using esbuild (which handles CJS interop nicely)
./node_modules/.bin/esbuild extract-soundfonts.mjs \
  --bundle \
  --platform=node \
  --format=esm \
  --sourcemap \
  --outfile=tmp/extract-soundfonts-bundle.mjs \
  --log-level=silent

# Run the bundled script
node --enable-source-maps tmp/extract-soundfonts-bundle.mjs
