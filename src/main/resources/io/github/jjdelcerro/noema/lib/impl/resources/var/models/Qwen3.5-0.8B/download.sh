#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

URL_BASE="https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main"

# Función auxiliar para descargar con curl (o wget de respaldo) de forma atómica y segura
download_file() {
    local url="$1"
    local output="$2"
    local temp_output="${output}.tmp"

    mkdir -p "$(dirname "$output")"

    if [ -f "$output" ]; then
        echo "Ya existe: $output"
        return 0
    fi

    echo "Descargando: $(basename "$output")..."

    # Ponemos la descarga directamente en el 'if'
    # Si curl/wget termina con código 0, entra al 'then'; si falla, entra al 'else'
    local download_success=false

    if command -v curl &> /dev/null; then
        if curl -fL --progress-bar -o "$temp_output" "$url"; then
            download_success=true
        fi
    else
        if wget -q --show-progress -O "$temp_output" "$url"; then
            download_success=true
        fi
    fi

    if [ "$download_success" = true ]; then
        mv "$temp_output" "$output"
        echo "Guardado con éxito en: $output"
    else
        echo "ERROR: Falló la descarga de $(basename "$output") desde $url" >&2
        rm -f "$temp_output"  # Limpieza del temporal corrupto
        return 1               # Notifica el fallo hacia arriba
    fi
}

echo "Comprobando archivos del modelo en: ${TARGET_DIR}"

# Comprobar y descargar cada archivo (fíjate que el último ya NO lleva barra invertida)
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
  "tokenizer_config.json"
do
  download_file "${URL_BASE}/${ff}?download=true" "${TARGET_DIR}/${ff}"
done

echo "Listo"
