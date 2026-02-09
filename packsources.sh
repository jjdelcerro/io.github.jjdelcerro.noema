#!/bin/bash
cd $(dirname $0)
find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.git" -o -path "$PWD/docs" -o -path "$PWD/target" \) -prune -o -print | packfiles >tmp/poc-sources.txt
