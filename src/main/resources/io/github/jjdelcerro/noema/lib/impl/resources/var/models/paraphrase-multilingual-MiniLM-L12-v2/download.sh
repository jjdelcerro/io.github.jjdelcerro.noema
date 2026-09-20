#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

# URLs de los archivos en Hugging Face (Xenova)
URL_MODEL="https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx?download=true"
URL_TOKENIZER="https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json?download=true"

# Rutas locales finales
FILE_MODEL="${TARGET_DIR}/model_quantized.onnx"
FILE_TOKENIZER="${TARGET_DIR}/tokenizer.json"

# Función auxiliar para descarga atómica y segura
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
        rm -f "$temp_output"
        return 1
    fi
}

echo "Comprobando archivos del modelo en: ${TARGET_DIR}"

# Comprobar y descargar cada archivo
download_file "$URL_TOKENIZER" "$FILE_TOKENIZER"
download_file "$URL_MODEL" "$FILE_MODEL"

echo "Listo"