#!/bin/bash
cd $(dirname $0)
find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.git" -o -path "$PWD/docs" -o -path "$PWD/target" -o -path "$PWD/DEVELOPMENT_STATUS.md" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt
