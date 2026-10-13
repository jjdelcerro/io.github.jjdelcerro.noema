#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

. $SCRIPT_DIR/../downloadutils.sh

echo "Comprobando archivos del modelo en: ${TARGET_DIR}"

URL_BASE="https://huggingface.co/onnx-community/Qwen3-0.6B-ONNX/resolve/main/onnxruntime/cpu_and_mobile/cpu-int4-kld-block-128"
rm -f "${TARGET_DIR}/files.txt"

for ff in \
  "chat_template.jinja" \
  "config.json" \
  "genai_config.json" \
  "model.onnx" \
  "tokenizer.json" \
  "tokenizer_config.json"
do
  download_file "${URL_BASE}/${ff}?download=true" "${TARGET_DIR}/${ff}"
  echo "$(basename ${ff})" >>"${TARGET_DIR}/files.txt"
done

echo "Listo"
