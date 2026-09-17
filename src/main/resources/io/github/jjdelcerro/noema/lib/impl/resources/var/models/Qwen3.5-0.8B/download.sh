#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

URL_BASE="https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main"

# Función auxiliar para descargar con curl (o wget de respaldo)
download_file() {
    local url="$1"
    local output="$2"

    mkdir -p "$(dirname "$output")"

    if [ -f "$output" ]; then
        echo "Ya existe: $output"
    else
        echo "Descargando: $(basename "$output")..."
        if command -v curl &> /dev/null; then
            curl -L -o "$output" "$url"
        else
            wget -O "$output" "$url"
        fi
        echo "Guardado en $output"
    fi
}

echo "Comprobando archivos del modelo en: ${TARGET_DIR}"

# Comprobar y descargar cada archivo
for ff in \
  "onnx/decoder_model_merged_q4.onnx" \
  "onnx/decoder_model_merged_q4.onnx_data" \
  "onnx/embed_tokens_q4.onnx" \
  "onnx/embed_tokens_q4.onnx_data" \
  "README.md" \
  "chat_template.jinja" \
  "config.json" \
  "generation_config.json" \
  "preprocessor_config.json" \
  "processor_config.json" \
  "tokenizer.json" \
  "tokenizer_config.json" \
  do
  download_file "${URL_BASE}/${ff}?download=true" "${TARGET_DIR}/${ff}"
done

echo "Listo"
