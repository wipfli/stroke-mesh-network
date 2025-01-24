import json

def create_geojson_lines(file_path, delimiter=','):
    features = []

    with open(file_path, 'r') as file:
        # Skip the header line
        next(file)
        
        for line in file:
            values = line.strip().split(delimiter)
            
            # Parse values
            way_id = int(values[0])
            start_lon = int(values[1]) / 1e7
            start_lat = int(values[2]) / 1e7
            end_lon = int(values[3]) / 1e7
            end_lat = int(values[4]) / 1e7
            visits = int(values[5])
            
            # Add GeoJSON line feature
            features.append({
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [
                        [start_lat, start_lon],
                        [end_lat, end_lon]
                    ]
                },
                "properties": {
                    "visits": f"{visits:.2e}"
                }
            })
    
    # Create GeoJSON FeatureCollection
    geojson = {
        "type": "FeatureCollection",
        "features": features
    }
    
    return geojson

# Example usage
file_path = 'bretagne_uni.csv'  # Replace with your file path
print('create lines...')
geojson = create_geojson_lines(file_path)

# Save to a file or print
print('write to file...')
with open('output.geojson', 'w') as f:
    json.dump(geojson, f, indent=2)

