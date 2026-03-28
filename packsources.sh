#!/bin/bash
cd $(dirname $0)
find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.g*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/target" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt

#find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.g*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/docs" -o -path "$PWD/target" -o -path "$PWD/DEVELOPMENT_STATUS.md" -o -path "$PWD/AGENT_CONTEXT.md" -o -path "$PWD/README.md" -o -path "$PWD/screenshot.png" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt
