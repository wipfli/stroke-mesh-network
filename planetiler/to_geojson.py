import json

def linestring_to_geojson(wkt_linestring):
    """
    Convert a WKT LINESTRING into a GeoJSON LineString object.

    Parameters:
        wkt_linestring (str): A WKT LINESTRING, e.g., 
                              "LINESTRING (7.6028158 46.7426411, 7.6030486 46.7433392)"
    
    Returns:
        dict: GeoJSON LineString object.
    """
    # Remove the "LINESTRING" keyword and parentheses
    coords_text = wkt_linestring.replace("LINESTRING", "").replace("(", "").replace(")", "").strip()
    
    # Split the coordinates into pairs
    coordinates = [
        list(map(float, pair.split()))
        for pair in coords_text.split(",")
    ]
    
    # Create GeoJSON LineString
    geojson = {
        "type": "LineString",
        "coordinates": coordinates
    }
    
    return geojson

with open('linestrings.txt') as f:
    line = f.readline()
    while line != '':
        geojson = linestring_to_geojson(line)
        print(json.dumps(geojson))
        line = f.readline()