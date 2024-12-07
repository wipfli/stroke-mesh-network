#! /bin/bash

# docker run --rm -t -v "${PWD}/data:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/switzerland.osm.pbf 
# docker run --rm -t -v "${PWD}/data:/data" osrm/osrm-backend osrm-partition /data/switzerland.osrm
# docker run --rm -t -v "${PWD}/data:/data" osrm/osrm-backend osrm-customize /data/switzerland.osrm
docker run -it -p 5000:5000 --rm -v "${PWD}/data:/data" osrm/osrm-backend osrm-routed --algorithm mld /data/switzerland.osrm