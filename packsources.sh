#!/bin/bash
cd $(dirname $0)
# Con todo
find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.g*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/target" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt

# Para el informe de estado
#find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.g*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/target" -o -path "$PWD/DEVELOPMENT_STATUS.md" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt

# Para el informe de desarrollo 
#find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.g*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/docs" -o -path "$PWD/target" -o -path "$PWD/DEVELOPMENT_STATUS.md" -o -path "$PWD/AGENT_CONTEXT.md" -o -path "$PWD/README.md" -o -path "$PWD/screenshot.png" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt

