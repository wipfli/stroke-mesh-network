import requests
import random
import json
import math

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

# lat1, lon1 = 47.36705342, 8.54085504  # Berlin
# start = Coordinate(lat=lat1, lon=lon1)
# lat2, lon2 = 47.36743532, 8.54238649   # Paris
# end = Coordinate(lat=lat2, lon=lon2)

# distance = haversine(start, end)
# print(f"The distance between Berlin and Paris is {distance:.2f} m")
# exit(1)

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
    _id_counter = 0
    def __init__(self, start_node, end_node, coordinates):
        self.start_node = start_node
        self.end_node = end_node
        self.visits = 0
        self.after_end_map = {} # next coordinate after end -> count
        self.coordinates = coordinates
        self.length = sum([haversine(coordinates[i], coordinates[i + 1]) for i in range(len(coordinates) - 1)])
        self.id = Edge._id_counter
        Edge._id_counter += 1

    def visit(self):
        self.visits += 1
    
    def bump_after_end(self, coordinate):
        if coordinate not in self.after_end_map:
            self.after_end_map[coordinate] = 0
        self.after_end_map[coordinate] += 1

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
        for edge in node.edge_map.values():
            after_list = []
            for coordinate in edge.after_end_map:
                edge_id = edge.end_node.get_edge(coordinate).id
                count = edge.after_end_map[coordinate]
                after_list += [edge_id, count]

            feature = {
                "type": "Feature",
                "properties": {
                    "edge_id": edge.id,
                    "length": edge.length,
                    "visits": edge.visits,
                    "after_list": json.dumps([edge.id, edge.visits] + after_list),                
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

def get_sorted_edges(node_map):
    result = []
    for node in node_map.values():
        for edge in node.edge_map.values():
            result.append(edge)

    result.sort(key=lambda edge: -edge.visits)
    return result

# def edges_to_geojson(edges):
#     features = []

#     for edge in edges:
#         after_list = []
#         for coordinate in edge.after_end_map:
#             edge_id = edge.end_node.get_edge(coordinate).id
#             count = edge.after_end_map[coordinate]
#             after_list += [edge_id, count]

#         feature = {
#             "type": "Feature",
#             "properties": {
#                 "edge_id": edge.id,
#                 "length": edge.length,
#                 "visits": edge.visits,
#                 "after_list": json.dumps([edge.id, edge.visits] + after_list),                
#             },
#             "geometry": {
#                 "type": "LineString",
#                 "coordinates": [list(c) for c in edge.coordinates]
#             }
#         }
#         features.append(feature)

#     return {
#         "type": "FeatureCollection",
#         "features": features
#     }

def simulate(n, reverse_routes=False):
    start_end_pairs = get_building_location_pairs(n=n)

    node_map = {} # Coordinate -> Node
    for start, end in start_end_pairs:
        router = Router(start, end)
        original_route = router.get_route()
        reversed_route = list(reversed(original_route))
        for route in [original_route, reversed_route]:
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

                after_end = route[i + 2] if i + 2 < len(route) else None
                if after_end is not None:
                    edge.bump_after_end(after_end)

            if not reverse_routes:
                break
    
    return node_map

node_map = simulate(n=1000, reverse_routes=True)
geojson = to_geojson(node_map)

with open('output.json', 'w') as f:
    json.dump(geojson, f, indent=2)

# sorted_edges = get_sorted_edges(node_map)

# for edge in sorted_edges:
#     print(edge.id, edge.visits, edge.coordinates)
# edge = sorted_edges[2]

# removed = set({})
# edges = []
# last_visits = edge.visits

# while True:
#     # if edge.visits > last_visits:
#     #     break
#     last_visits = edge.visits
#     if edge.id in removed:
#         break
#     removed.add(edge.id)
#     edges.append(edge)
#     if edge.after_end_map == {}:
#         break
#     after_end = max(edge.after_end_map, key=edge.after_end_map.get)
#     edge = edge.end_node.get_edge(after_end)

# geojson = edges_to_geojson(edges)
# with open('output.json', 'w') as f:
#     json.dump(geojson, f, indent=2)
