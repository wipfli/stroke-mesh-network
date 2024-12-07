# Random OSRM Routing for Road Network Importance

## Steps

```
mkdir data
```

Download `switzerland.osm.pbf` to the `data/` folder. 

```
./prepare-osrm.sh
```

Start the OSRM server on port 5000 with:

```
./run-osrm.sh
```

Extract buildings in Zürich to geojson:

```
./filter-buildings.sh
```

Extract one point per building :
```
python3 filter_buildings.py
```

Do some random routes:

```
python3 router.py
```

Make tiles:

```
./tippecanoe.sh
```

Start http server for demo map:

```
npx serve --debug . -p 3000
```

Open `http://localhost:3000

