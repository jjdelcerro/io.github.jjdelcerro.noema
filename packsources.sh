#!/bin/bash
cd $(dirname $0)
find $PWD -path "$PWD/tmp" -prune -o -print | packfiles >tmp/sources.txt
