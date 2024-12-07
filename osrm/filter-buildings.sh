#! /bin/bash
osmium extract -b 8.494271,47.333464,8.585595,47.39374 data/switzerland.osm.pbf -o zurich.osm.pbf --overwrite
osmium tags-filter zurich.osm.pbf "w/building=*" -o buildings.osm.pbf --overwrite
osmium export buildings.osm.pbf -o data/buildings.geojson --overwrite
rm zurich.osm.pbf
rm buildings.osm.pbf
