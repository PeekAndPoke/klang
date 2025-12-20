#!/bin/bash

# goto directory of this script
cd "$(dirname "$0")" || exit

echo "Running jssrc/build.sh in directory:"
pwd

npm install

./node_modules/.bin/esbuild ./strudel-entry.mjs \
--bundle \
--format=esm \
--platform=browser \
--outfile=../build/strudel-bundle.mjs
