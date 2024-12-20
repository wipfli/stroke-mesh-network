import requests
import random
import json
import math
import time

class Coordinate(tuple):
    def __new__(cls, lat: float, lon: float):
        return super().__new__(cls, (lon, lat))

    @property
    def lat(self):
        return self[1]

    @property
    def lon(self):
        return self[0]

    def __repr__(self):
        return f"Coordinate(lat={self.lat}, lon={self.lon})"

class BBox(tuple):
    def __new__(cls, min_lon: float, min_lat: float, max_lon: float, max_lat: float):
        return super().__new__(cls, (min_lon, min_lat, max_lon, max_lat))
    
    @property
    def min_lon(self):
        return self[0]
    
    @property
    def min_lat(self):
        return self[1]
    
    @property
    def max_lon(self):
        return self[2]
    
    @property
    def max_lat(self):
        return self[3]
    
    def __repr__(self):
        return f"BBox(min_lon={self.min_lon}, min_lat={self.min_lat}, max_lon={self.max_lon}, max_lat={self.max_lat})"
    
    def contains(self, coordinate: Coordinate):
        return self.min_lon < coordinate.lon < self.max_lon and self.min_lat < coordinate.lat < self.max_lat

class Router:
    def __init__(self, start, end, osrm_url="http://localhost:5000"):
        self.start = start
        self.end = end
        self.osrm_url = osrm_url

    def get_route(self):
        result = []
        start_str = f"{self.start.lon},{self.start.lat}"
        end_str = f"{self.end.lon},{self.end.lat}"
        url = f"{self.osrm_url}/route/v1/driving/{start_str};{end_str}?overview=full"
        params = {
            "overview": "full",
            "geometries": "geojson"
        }
        response = requests.get(url, params=params)
        route = response.json()
        distance = 0
        if "routes" in route:
            distance = route["routes"][0]["distance"]
            for c in route["routes"][0]["geometry"]["coordinates"]:
                result.append(Coordinate(lon=c[0], lat=c[1]))
        
        return result, distance

# router = Router(
#     start=Coordinate(
#         lat=47.36337716, 
#         lon=8.52414747
#     ),
#     end=Coordinate(
#         lat=47.44931946, 
#         lon=8.00554909
#     )
# )

# print(router.get_route())

def get_building_location_pairs(n):
    result = []
    with open("building-coordinates.json") as f:
        buildings = json.load(f)

    selection1 = random.choices(buildings, k=n)
    selection2 = random.choices(buildings, k=n)

    for c1, c2  in zip(selection1, selection2):
        start = Coordinate(lon=c1[0], lat=c1[1])
        end = Coordinate(lon=c2[0], lat=c2[1])
        result.append([start, end])

    return result

def get_village_location_pairs(n: int, bbox: BBox):
    result = []
    with open("village-coordinates.json") as f:
        villages = [Coordinate(lon=c[0], lat=c[1]) for c in json.load(f)]
    
    villages = [c for c in villages if bbox.contains(c)]
    
    while len(result) < n:
        selection1 = random.choices(villages, k=1000)
        selection2 = random.choices(villages, k=1000)

        for start, end  in zip(selection1, selection2):
            distance = haversine(start, end)
            min_distance = 100_000
            if min_distance < distance:
                result.append([start, end])
                if len(result) == n:
                    break

    return result


def haversine(start: Coordinate, end: Coordinate):
    lat1, lon1, lat2, lon2 = map(math.radians, [start.lat, start.lon, end.lat, end.lon])

    # Haversine formula
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    a = math.sin(dlat / 2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    R = 6371000
    distance = R * c

    return distance # meters

# coordinates = [
#     [
#         8.534625,
#         47.392648
#     ],
#     [
#         8.534615,
#         47.392657
#     ],
#     [
#         8.534522,
#         47.392744
#     ]
# ]
# for i in range(len(coordinates) - 1):
#     lon1, lat1 = coordinates[i]
#     lon2, lat2 = coordinates[i + 1]
#     start = Coordinate(lat=lat1, lon=lon1)
#     end = Coordinate(lat=lat2, lon=lon2)
#     distance = haversine(start, end)
#     print(distance)
# exit(1)

class Node:
    def __init__(self, coordinate):
        self.coordinate = coordinate
        self.edges = []
    
    def add_edge(self, edge):
        for other in self.edges:
            if other.coordinates == edge.coordinates:
                return
        self.edges.append(edge)
    
    def remove_edge(self, edge):
        index = -1
        for i, other in enumerate(self.edges):
            if other.coordinates == edge.coordinates:
                index = i
                break
        if index > -1:
            self.edges.pop(index)

    def __repr__(self):
        return f"Node(coordinate={self.coordinate}, edges={self.edges})"

class Edge:
    _id_counter = 0

    def __init__(self, start_node, end_node, coordinates, length, main=True, reversed_edge=None):
        self.id = Edge._id_counter
        self.start_node = start_node
        self.end_node = end_node
        self.length = length
        self.coordinates = coordinates
        self.main = main
        self.reversed = reversed_edge
        self.visits = 0
        self.removed = False

        if main:
            reversed_coordinates = list(reversed(coordinates))
            self.reversed = Edge(end_node, start_node, reversed_coordinates, length, main=False, reversed_edge=self)
            Edge._id_counter += 1
    
    def remove(self):
        if not self.removed:
            self.start_node.remove_edge(self)
            self.end_node.remove_edge(self.reversed)
            self.removed = True

    def is_loop(self):
        return self.start_node.coordinate == self.end_node.coordinate

    def is_stub(self):
        return not self.removed and (len(self.start_node.edges) == 1 or len(self.end_node.edges) == 1 or self.is_loop())
    
    def __hash__(self):
        return hash(tuple(self.coordinates))
    
    def __eq__(self, other):
        return isinstance(other, Edge) and self.coordinates == other.coordinates

    def __repr__(self):
        return f"Edge(start={self.start_node.coordinate}, end={self.end_node.coordinate}, visits={self.visits}, coordinates={self.coordinates}, length={self.length:.1f})"

def get_node(nodes, coordinate):
    if coordinate not in nodes:
        nodes[coordinate] = Node(coordinate)
    return nodes[coordinate]

def to_geojson(node_map):
    features = []

    for node in node_map.values():
        # feature = {
        #     "type": "Feature",
        #     "properties": {
        #         "num_edges": len(node.edges),              
        #     },
        #     "geometry": {
        #         "type": "Point",
        #         "coordinates": list(node.coordinate)
        #     }
        # }
        # features.append(feature)
        for edge in node.edges:

            feature = {
                "type": "Feature",
                "properties": {
                    "edge_id": edge.id,
                    "length": edge.length,
                    "visits": edge.visits,            
                },
                "geometry": {
                    "type": "LineString",
                    "coordinates": [list(c) for c in edge.coordinates]
                }
            }
            features.append(feature)

    return {
        "type": "FeatureCollection",
        "features": features
    }

def simulate(n, bbox):
    print("getting location pairs...", flush=True)
    start_end_pairs = get_village_location_pairs(n=n, bbox=bbox)
    print("done.", flush=True)
    node_map = {} # Coordinate -> Node
    ii = 0
    for start, end in start_end_pairs:
        if ii % 10 == 0:
            print(ii, flush=True)
        ii += 1
        router = Router(start, end)
        route, distance = router.get_route()
        # reversed_route = list(reversed(original_route))

        for i in range(len(route) - 1):
            start = route[i]
            end = route[i + 1]     

            start_node = get_node(node_map, start)
            end_node = get_node(node_map, end)

            edge = None
            for existing_edge in start_node.edges:
                if existing_edge.end_node.coordinate == end_node.coordinate:
                    edge = existing_edge
                    break
            if edge is None:
                coordinates = [start, end]
                length = haversine(start, end)
                edge = Edge(start_node, end_node, coordinates, length)
                start_node.add_edge(edge)
                end_node.add_edge(edge.reversed)
            edge.visits += 1
            edge.reversed.visits += 1
    
    return node_map

if __name__ == '__main__':
    tic = time.time()

    bbox = BBox(6.44,42.62,13.41,47.29) # N Italy large

    print("simulating...", flush=True)

    node_map = simulate(n=10, bbox=bbox)

    print("to_geojson...", flush=True)
    geojson = to_geojson(node_map)

    print("writing...", flush=True)
    with open('output.json', 'w') as f:
        json.dump(geojson, f)
    
    print("router.py", int(time.time() - tic), "s")
