#!/usr/bin/env bash
set -e

# Directorio donde está ubicado este script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Carpeta de destino para los archivos del modelo
TARGET_DIR="${SCRIPT_DIR}"

# URLs de los archivos
URL_MODEL="https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx?download=true"
URL_TOKENIZER="https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json?download=true"

# Rutas locales finales
FILE_MODEL="${TARGET_DIR}/model_quantized.onnx"
FILE_TOKENIZER="${TARGET_DIR}/tokenizer.json"

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
download_file "$URL_TOKENIZER" "$FILE_TOKENIZER"
download_file "$URL_MODEL" "$FILE_MODEL"

echo "Listo"