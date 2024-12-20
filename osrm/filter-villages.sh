#! /bin/bash
osmium tags-filter data/europe-latest.osm.pbf "n/place=village" "n/place=town" "n/place=city" -o villages.osm.pbf --overwrite
osmium export villages.osm.pbf -o data/villages.geojson --overwrite
rm villages.osm.pbf
