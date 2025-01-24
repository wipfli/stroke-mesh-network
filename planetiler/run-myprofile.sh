#!/bin/bash
java -cp planetiler.jar MyProfile.java
# docker run --rm -it -v "$(pwd)/data":/data -p 8080:8080 maptiler/tileserver-gl -p 8080
serve --debug . -p 3000 --cors
