#!/bin/bash
set -e

# Directorio de destino
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
#VENDOR_DIR="$SCRIPT_DIR/src/main/resources/webapp/vendor"
VENDOR_DIR="$SCRIPT_DIR"

echo "Preparando directorio vendor: $VENDOR_DIR"
mkdir -p "$VENDOR_DIR"

# ---------------- marked ----------------------
MARKED_VERSION="12.0.2"
MARKED_URL="https://cdn.jsdelivr.net/npm/marked@${MARKED_VERSION}/marked.min.js"

echo "Descargando marked.min.js (v${MARKED_VERSION})..."
curl -fsSL "$MARKED_URL" -o "$VENDOR_DIR/marked.min.js"

# ---------------- purify ----------------------
DOMPURIFY_VERSION="3.1.2"
DOMPURIFY_URL="https://cdn.jsdelivr.net/npm/dompurify@${DOMPURIFY_VERSION}/dist/purify.min.js"
echo "Descargando purify.min.js (v${DOMPURIFY_VERSION})..."
curl -fsSL "$DOMPURIFY_URL" -o "$VENDOR_DIR/purify.min.js"


# ---------------- file-tree ----------------------
FILE_TREE_VERSION="v0.1.5"
mkdir -p "$VENDOR_DIR/file-tree"
echo "Descargando file-tree (${FILE_TREE_VERSION})..."
curl -fsSL "https://raw.githubusercontent.com/WebReflection/file-tree/refs/tags/${FILE_TREE_VERSION}/prod.js" -o "$VENDOR_DIR/file-tree/prod.js"
curl -fsSL "https://raw.githubusercontent.com/WebReflection/file-tree/refs/tags/${FILE_TREE_VERSION}/prod.css" -o "$VENDOR_DIR/file-tree/prod.css"

echo "Descarga de librerias vendor completada."
tree -shAn "$VENDOR_DIR"
