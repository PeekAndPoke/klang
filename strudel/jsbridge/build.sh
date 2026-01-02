#!/bin/bash

# goto directory of this script
cd "$(dirname "$0")" || exit

echo "Running strudel jsbridge build.sh in directory:"
pwd

pnpm install

echo "Creating esm module"
./node_modules/.bin/esbuild ./strudel-entry.mjs \
  --bundle \
  --format=esm \
  --platform=browser \
  --sourcemap \
  --legal-comments=none \
  --keep-names \
  --tree-shaking=false \
  --outfile=./../src/jvmMain/resources/strudel-bundle.mjs
