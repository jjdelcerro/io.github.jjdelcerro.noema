#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

. $SCRIPT_DIR/../downloadutils.sh

echo "Comprobando archivos del modelo en: ${TARGET_DIR}"

URL_BASE="https://huggingface.co/onnx-community/MiniCPM5-1B/resolve/main"

for ff in \
  "chat_template.jinja" \
  "genai_config.json" \
  "model.onnx" \
  "model.onnx.data" \
  "tokenizer.json" \
  "tokenizer_config.json"
do
  download_file "${URL_BASE}/${ff}?download=true" "${TARGET_DIR}/${ff}"
done

echo "Listo"
