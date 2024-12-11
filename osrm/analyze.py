import json
import router

def get_json(filename):
    result = {}
    with open(filename) as f:
        result = json.load(f)
    return result


def read(filename):
    data = get_json(filename)

    node_map = {} # Coordinate -> Node

    for feature in data["features"]:
        start = router.Coordinate(
            lon=feature["geometry"]["coordinates"][0][0],
            lat=feature["geometry"]["coordinates"][0][1]
        )
        end = router.Coordinate(
            lon=feature["geometry"]["coordinates"][1][0],
            lat=feature["geometry"]["coordinates"][1][1]
        )
            
        start_node = router.get_node(node_map, start)
        end_node = router.get_node(node_map, end)

        edge = start_node.get_edge(end)
        if edge is None:
            edge = router.Edge(start_node, end_node, [start, end])
            start_node.add_edge(edge)
        
        edge.visits = feature["properties"]["visits"]
    
    return node_map

def sum_reverse_visits(node_map):
    visit_map = {} # (start, end) -> visits
    for node in node_map.values():
        for edge in node.edge_map.values():
            visit_map[(
                edge.start_node.coordinate, 
                edge.end_node.coordinate
            )] = edge.visits

    for node in node_map.values():
        for edge in node.edge_map.values():
            reverse_visits = visit_map.get((
                edge.end_node.coordinate,
                edge.start_node.coordinate
            ), edge.visits)
            edge.visits += reverse_visits     

    return node_map

node_map = read('output.json')
# node_map = sum_reverse_visits(node_map)
geojson = router.to_geojson(node_map)

with open('output2.json', 'w') as f:
    json.dump(geojson, f)

