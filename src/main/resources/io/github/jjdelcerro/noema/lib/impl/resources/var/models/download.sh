#!/usr/bin/env bash
set -e
for ff in \
  "paraphrase-multilingual-MiniLM-L12-v2" \
  ºQwen3.5-0.8B" \
  do
  chmod a+x "${ff}/download.sh"
  "./${ff}/download.sh"
done
