#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

. $SCRIPT_DIR/../downloadutils.sh

echo "Comprobando archivos del modelo en: ${TARGET_DIR}"

URL_BASE="https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main"

for ff in \
  "onnx/model_quantized.onnx" \
  "tokenizer.json"
do
  download_file "${URL_BASE}/${ff}?download=true" "${TARGET_DIR}/$(basename ${ff})"
done

echo "Listo"
