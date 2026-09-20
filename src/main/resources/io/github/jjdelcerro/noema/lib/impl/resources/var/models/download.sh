#!/usr/bin/env bash
set -e

# Asegurar que nos situamos en la carpeta donde reside este script
cd "$(dirname "$0")"

for ff in \
  "paraphrase-multilingual-MiniLM-L12-v2" \
  "Qwen3.5-0.8B"
do
  chmod a+x "${ff}/download.sh"
  "./${ff}/download.sh"
done