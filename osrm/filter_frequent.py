import json

def read(filename):
    data = {}
    with open(filename) as f:
        data = json.load(f)
    return data

def filter_frequent(data, threshold):
    result = []
    features = data['features']
    for feature in features:
        if feature['properties']['frequency'] >= threshold:
            result.append(feature)
    return result

data = read('zurich.json')
threshold = 0.5

print(filter_frequent(data, threshold))
