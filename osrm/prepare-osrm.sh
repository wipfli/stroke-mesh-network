#! /bin/bash

docker run --rm -t -v "${PWD}/data:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/switzerland.osm.pbf 
docker run --rm -t -v "${PWD}/data:/data" osrm/osrm-backend osrm-partition /data/switzerland.osrm
docker run --rm -t -v "${PWD}/data:/data" osrm/osrm-backend osrm-customize /data/switzerland.osrm
