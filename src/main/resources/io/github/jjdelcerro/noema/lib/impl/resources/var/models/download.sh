#!/usr/bin/env bash
set -e
#
# https://huggingface.co/onnx-community/Qwen3-0.6B-DQ-ONNX/tree/main
# https://huggingface.co/onnx-community/Qwen3-0.6B-ONNX/tree/main/onnxruntime/cpu_and_mobile/cpu-int4-kld-block-128
# https://huggingface.co/onnx-community/MiniCPM5-1B/tree/main

cd "$(dirname "$0")"

for ff in \
  "paraphrase-multilingual-MiniLM-L12-v2" \
  "Qwen3-0.6B"
  "MiniCPM5-1B"
do
  chmod a+x "${ff}/download.sh"
  "./${ff}/download.sh"
done
