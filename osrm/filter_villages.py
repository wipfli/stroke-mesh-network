import json

def extract_village_coordinates():
    result = []
    with open("data/villages.geojson") as f:
        villages = json.load(f)

    for feature in villages["features"]:
        result.append(feature["geometry"]["coordinates"])

    return result

with open('village-coordinates.json', 'w') as f:
    json.dump(extract_village_coordinates(), f)