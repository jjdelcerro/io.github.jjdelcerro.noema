#!/bin/bash
set -e

# Directorio de destino
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
#VENDOR_DIR="$SCRIPT_DIR/src/main/resources/webapp/vendor"
VENDOR_DIR="$SCRIPT_DIR"

# Versiones fijadas de las dependencias
MARKED_VERSION="12.0.2"
DOMPURIFY_VERSION="3.1.2"

MARKED_URL="https://cdn.jsdelivr.net/npm/marked@${MARKED_VERSION}/marked.min.js"
DOMPURIFY_URL="https://cdn.jsdelivr.net/npm/dompurify@${DOMPURIFY_VERSION}/dist/purify.min.js"

echo "Preparando directorio vendor: $VENDOR_DIR"
mkdir -p "$VENDOR_DIR"

echo "Descargando marked.min.js (v${MARKED_VERSION})..."
curl -fsSL "$MARKED_URL" -o "$VENDOR_DIR/marked.min.js"

echo "Descargando purify.min.js (v${DOMPURIFY_VERSION})..."
curl -fsSL "$DOMPURIFY_URL" -o "$VENDOR_DIR/purify.min.js"

echo "Descarga de librerias vendor completada."
ls -lh "$VENDOR_DIR"
