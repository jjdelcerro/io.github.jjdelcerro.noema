#!/bin/bash
cd $(dirname $0)
# Con todo
#find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.??*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/target" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt

# Para el informe de estado
#find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.??*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/target" -o -path "$PWD/DEVELOPMENT_STATUS.md" \) -prune -o -print | packfiles >tmp/noema-agent-sources.txt

# Para el informe de desarrollo 
( find $PWD \( -path "$PWD/tmp" -o -path "$PWD/.??*" -o -path "$PWD/nb-configuration.xml" -o -path "$PWD/docs" -o -path "$PWD/target" -o -path "$PWD/DEVELOPMENT_STATUS.md" -o -path "$PWD/OVERVIEW.md" -o -path "$PWD/README.md" -o -path "$PWD/project-map.md" -o -path "$PWD/screenshot.png" -o -path "$PWD/src/main/resources/io/github/jjdelcerro/noema/lib/impl/resources/var/models" -o -path "$PWD/src/test/resources/fixtures/datos-test.md" -o -path "$PWD/src/main/resources/webapp/vendor" \) -prune -o -print ; find $PWD/src/main/resources/io/github/jjdelcerro/noema/lib/impl/resources/var/models -name ".gitignore" -o -name "*.sh" ) | packfiles $* >tmp/noema-agent-sources.txt
