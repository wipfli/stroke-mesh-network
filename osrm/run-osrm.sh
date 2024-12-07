#! /bin/bash

docker run -it -p 5000:5000 --rm -v "${PWD}/data:/data" osrm/osrm-backend osrm-routed --algorithm mld /data/switzerland.osrm