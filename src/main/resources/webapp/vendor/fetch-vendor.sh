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

# ---------------- ace ----------------------
ACE_VERSION="1.44.0"
ACE_DIR="$VENDOR_DIR/ace"
mkdir -p "$ACE_DIR"
ACE_BASE="https://cdn.jsdelivr.net/npm/ace-builds@${ACE_VERSION}/src-min-noconflict"

echo "Descargando ace (v${ACE_VERSION})..."
curl -fsSL "${ACE_BASE}/ace.js" -o "$ACE_DIR/ace.js"

for mode in java xml json markdown sh groovy properties yaml html css javascript; do
  echo "  -> mode-${mode}.js"
  curl -fsSL "${ACE_BASE}/mode-${mode}.js" -o "$ACE_DIR/mode-${mode}.js"
done

for f in theme-dracula.js ext-searchbox.js; do
  echo "  -> ${f}"
  curl -fsSL "${ACE_BASE}/${f}" -o "$ACE_DIR/${f}"
done


echo "Descarga de librerias vendor completada."
tree -shAn "$VENDOR_DIR"
