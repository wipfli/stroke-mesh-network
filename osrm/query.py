import requests
import json
import random
import time

def get_linestring_feature(from_lon, from_lat, to_lon, to_lat):
    base_url = "http://localhost:5000"
    coords_from = f"{from_lon},{from_lat}"
    coords_to = f"{to_lon},{to_lat}"
    url = f"{base_url}/route/v1/driving/{coords_from};{coords_to}?overview=full"
    params = {
        "overview": "full",  # Get the complete route geometry
        "geometries": "geojson"  # Request GeoJSON format
    }
    response = requests.get(url, params=params)
    route = response.json()
    if "routes" in route:
        return {
            "type": "Feature",
            "geometry": route["routes"][0]["geometry"]
        }
    else: 
        return {
            "type": "Feature",
            "geometry": {
                "type": "LineString",
                "coordinates": []
            }
            
        }

def get_point_coordinates():
    result = []
    with open("villages.geojson") as f:
        pois = json.load(f)
        for feature in pois["features"]:
            result.append(feature["geometry"]["coordinates"])
    return result
    


c1 = [
   8.52414747,
   47.36337716
]

c2 = [
    8.00554909,
    47.44931946
]

from_lon = c1[0]
from_lat = c1[1]
to_lon = c2[0]
to_lat = c2[1]

feature = get_linestring_feature(
    from_lon=from_lon,
    from_lat=from_lat,
    to_lon=to_lon,
    to_lat=to_lat
)

counts = {} # {segment: count}

def add_to_counts(coordinates, weight):
    for i in range(len(coordinates) - 1):
        c1 = coordinates[i]
        c2 = coordinates[i + 1]
        segment = (c1[0], c1[1], c2[0], c2[1])
        if segment in counts:
            counts[segment] += weight
        else:
            counts[segment] = weight



tic = time.time()

n_max = 10000

# with open("places-northern-italy.geojson") as f:
#     villages = json.load(f)

# populations = []
# for feature in villages["features"]:
#     population = feature["properties"].get("population")
#     if population is None:
#         population = 5000
#     else:
#         population = int(population)
#     populations.append(population)

# selection1 = random.choices(villages["features"], k=n_max, weights=populations)
# selection2 = random.choices(villages["features"], k=n_max, weights=populations)

with open("zuri.geojson") as f:
    buildings = json.load(f)

selection1 = random.choices(buildings["features"], k=n_max)
selection2 = random.choices(buildings["features"], k=n_max)

for feature1, feature2 in zip(selection1, selection2):
    try:
        print(f"{feature1['properties']['name']} - {feature2['properties']['name']}")
    except KeyError:
        pass
    if feature1["geometry"]["type"] == "Point":
        c1 = feature1["geometry"]["coordinates"]
        c2 = feature2["geometry"]["coordinates"]
    else:
        c1 = feature1["geometry"]["coordinates"][0][0]
        c2 = feature2["geometry"]["coordinates"][0][0]
    from_lon = c1[0]
    from_lat = c1[1]
    to_lon = c2[0]
    to_lat = c2[1]
    feature = get_linestring_feature(
        from_lon=from_lon,
        from_lat=from_lat,
        to_lon=to_lon,
        to_lat=to_lat
    )
    coordinates = feature["geometry"]["coordinates"]
    weight = 1

    add_to_counts(coordinates, weight)

geojson = {
    "type": "FeatureCollection",
    "features": []
}

max_count = max(counts.values())
for segment in counts:
    feature = {
        "type": "Feature",
        "geometry": {
            "type": "LineString",
            "coordinates": [
                [segment[0], segment[1]],
                [segment[2], segment[3]]
            ]
        },
        "properties": {
            "frequency": counts[segment] / max_count
        }
    }
    geojson["features"].append(feature)

with open('output.json', 'w') as f:
    json.dump(geojson, f, indent=2)

print('total duration ', time.time() - tic, 's')