import requests
import random
import json

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
        if "routes" in route:
            for c in route["routes"][0]["geometry"]["coordinates"]:
                result.append(Coordinate(lon=c[0], lat=c[1]))
        
        return result

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


class Node:
    def __init__(self, coordinate):
        self.coordinate = coordinate
        self.edge_map = {} # end coordinate -> edge
    
    def get_edge(self, end):
        return self.edge_map.get(end, None)
    
    def add_edge(self, edge):
        self.edge_map[edge.end_node.coordinate] = edge

    def __repr__(self):
        return f"Node(coordinate={self.coordinate}, edges={list(self.edge_map.values())})"

class Edge:
    def __init__(self, start_node, end_node, coordinates):
        self.start_node = start_node
        self.end_node = end_node
        self.visits = 0
        self.coordinates = coordinates

    def visit(self):
        self.visits += 1

    def __repr__(self):
        return f"Edge(start={self.start_node.coordinate}, end={self.end_node.coordinate}, visited_count={self.visits}, coordinates={self.coordinates})"

def get_node(nodes, coordinate):
    if coordinate not in nodes:
        nodes[coordinate] = Node(coordinate)
    return nodes[coordinate]

def to_geojson(node_map):
    features = []

    for node in node_map.values():
        for edge in node.edge_map.values():
            feature = {
                "type": "Feature",
                "properties": {
                    "visits": edge.visits
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



def simulate(n):
    start_end_pairs = get_building_location_pairs(n=n)

    node_map = {} # Coordinate -> Node
    for start, end in start_end_pairs:
        router = Router(start, end)
        route = router.get_route()
        for i in range(len(route) - 1):
            start = route[i]
            end = route[i + 1]

            start_node = get_node(node_map, start)
            end_node = get_node(node_map, end)

            edge = start_node.get_edge(end)
            if edge is None:
                edge = Edge(start_node, end_node, [start, end])
                start_node.add_edge(edge)
            edge.visit()
    
    geojson = to_geojson(node_map)
    with open('output.json', 'w') as f:
        json.dump(geojson, f)
    

simulate(10000)
