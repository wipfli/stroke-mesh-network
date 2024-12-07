import json

def extract_building_coordinates():
    result = []
    with open("data/buildings.geojson") as f:
        buildings = json.load(f)

    for feature in buildings["features"]:
        if feature["geometry"]["type"] != "MultiPolygon":
            continue
        result.append(feature["geometry"]["coordinates"][0][0][0])

    return result

with open('building-coordinates.json', 'w') as f:
    json.dump(extract_building_coordinates(), f)