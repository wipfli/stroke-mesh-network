#!/bin/bash
java -cp planetiler.jar Bretagne.java
docker run --rm -it -v "$(pwd)/data":/data -p 8080:8080 maptiler/tileserver-gl -p 8080
# java -cp planetiler.jar Bretagne.java > linestrings.txt
# tippecanoe -o data/test.pmtiles linestrings.geojson --layer output --force --maximum-zoom 13 --minimum-zoom 4 --drop-densest-as-needed
# serve --debug . -p 3000 --cors
