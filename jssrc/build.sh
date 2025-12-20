#!/bin/bash

./node_modules/.bin/esbuild ./strudel-entry.mjs \
--bundle \
--format=esm \
--platform=browser \
--outfile=../build/strudel-bundle.mjs
