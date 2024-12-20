import json
import router
import statistics
from heapq import heappush, heappop
from itertools import count
import time

def get_json(filename):
    result = {}
    with open(filename) as f:
        result = json.load(f)
    return result

def read_stefans_data(filename):
    node_map = {} # Coordinate -> Node

    with open(filename) as f:
        f.readline() # skip header
        line = f.readline()

        while line != '' and line != '\n':
            numbers = [int(n) for n in line.strip().split(',')]
            way_id, start_lat, start_lon, end_lat, end_lon, visits, deadend = numbers
            start_lon /= 10 ** 7
            start_lat /= 10 ** 7
            end_lon /= 10 ** 7
            end_lat /= 10 ** 7

            start = router.Coordinate(
                lon=start_lon,
                lat=start_lat
            )
            end = router.Coordinate(
                lon=end_lon,
                lat=end_lat
            )

            start_node = router.get_node(node_map, start)
            end_node = router.get_node(node_map, end)

            coordinates = [start, end]
            edge = None
            for other in start_node.edges:
                if other.coordinates == coordinates:
                    edge = other
                    break
            
            if edge is None:
                length = router.haversine(start, end)
                edge = router.Edge(start_node, end_node, coordinates, length)
                start_node.add_edge(edge)
                end_node.add_edge(edge.reversed)
            
            edge.visits = visits
            edge.reversed.visits = visits
            edge.id = way_id
            edge.reversed.id = way_id
        
            line = f.readline()

    return node_map


def read(filename):
    data = get_json(filename)

    node_map = {} # Coordinate -> Node

    for feature in data["features"]:
        if feature["geometry"]["type"] != "LineString":
            continue
        start = router.Coordinate(
            lon=feature["geometry"]["coordinates"][0][0],
            lat=feature["geometry"]["coordinates"][0][1]
        )
        end = router.Coordinate(
            lon=feature["geometry"]["coordinates"][-1][0],
            lat=feature["geometry"]["coordinates"][-1][1]
        )
            
        start_node = router.get_node(node_map, start)
        end_node = router.get_node(node_map, end)

        coordinates = [router.Coordinate(lon=c[0], lat=c[1]) for c in feature["geometry"]["coordinates"]]
        edge = None
        for other in start_node.edges:
            if other.coordinates == coordinates:
                edge = other
                break
        
        if edge is None:
            length = feature["properties"]["length"]
            edge = router.Edge(start_node, end_node, coordinates, length)
            start_node.add_edge(edge)
            end_node.add_edge(edge.reversed)
        
        edge.visits = feature["properties"]["visits"]
    
    return node_map

def degree_two_merge(node_map):
    to_remove = []
    for key in node_map:
        node = node_map[key]
        degree_two_merge_on_node(node)
        if len(node.edges) == 0:
            to_remove.append(key)
    for key in to_remove:
        del node_map[key]

def degree_two_merge_on_node(node: router.Node):
    if len(node.edges) == 2:
        a = node.edges[0]
        b = node.edges[1]
        # if one side is a loop, degree is actually > 2
        if not a.is_loop() and not b.is_loop():
            return merge_two_edges(node, a, b)
    return None

def merge_two_edges(node: router.Node, a: router.Edge, b: router.Edge):
    # node.remove_edge(a)
    # node.remove_edge(b)
    coordinates = []
    coordinates += list(reversed(a.coordinates))
    coordinates += list(b.coordinates[1:])
    c = router.Edge(a.end_node, b.end_node, coordinates, a.length + b.length)
    c.visits = int((a.visits * a.length + b.visits * b.length) / (a.length + b.length))
    c.reversed.visits = c.visits
    # a.end_node.remove_edge(a.reversed)
    # b.end_node.remove_edge(b.reversed)
    a.end_node.add_edge(c)
    if a.end_node.coordinate != b.end_node.coordinate:
        b.end_node.add_edge(c.reversed)
    a.remove()
    b.remove()
    return c


#   private boolean isShortStubEdge(Edge edge) {
#     return edge != null && !edge.removed && edge.length < stubMinLength &&
#       (edge.from.getEdges().size() == 1 || edge.to.getEdges().size() == 1 || edge.isLoop());
#   }

#   private void removeShortStubEdges() {
#     PriorityQueue<Edge> toCheck = new PriorityQueue<>(Comparator.comparingDouble(Edge::length));
#     for (var node : output) {
#       for (var edge : node.getEdges()) {
#         if (isShortStubEdge(edge)) {
#           toCheck.offer(edge);
#         }
#       }
#     }
def remove_stubs(node_map):
    to_check = []
    unique_counter = count()
    for node in node_map.values():
        for edge in node.edges:
            if edge.is_stub():
                heappush(to_check, (edge.length, next(unique_counter), edge))

    while to_check:
        _, _, edge = heappop(to_check)
        if edge.removed:
            continue
        if edge.is_stub():
            edge.remove()

        merged = degree_two_merge_on_node(edge.start_node)
        if merged is not None:
            heappush(to_check, (merged.length, next(unique_counter), merged))

        if len(edge.start_node.edges) == 1:
            other = edge.start_node.edges[0]
            if other.is_stub():
                heappush(to_check, (other.length, next(unique_counter), other))

        if edge.start_node.coordinate != edge.end_node.coordinate:
            merged = degree_two_merge_on_node(edge.end_node)
            if merged is not None:
                heappush(to_check, (merged.length, next(unique_counter), merged))
            if len(edge.end_node.edges) == 1:
                other = edge.end_node.edges[0]
                if other.is_stub():
                    heappush(to_check, (other.length, next(unique_counter), other))


def remove_empty_nodes(node_map):
    to_remove = []
    for key in node_map:
        if len(node_map[key].edges) == 0:
            to_remove.append(key)
    for key in to_remove:
        del node_map[key]

def filter_by_visits(node_map, min_visits):
    to_check = []
    unique_counter = count()
    for node in node_map.values():
        for edge in node.edges:
            if edge.visits < min_visits:
                heappush(to_check, (edge.visits, next(unique_counter), edge))
    
    while to_check:
        _, _, edge = heappop(to_check)
        if edge.removed:
            continue
        if edge.visits < min_visits:
            edge.remove()

        merged = degree_two_merge_on_node(edge.start_node)
        if merged is not None and merged.visits < min_visits:
            heappush(to_check, (merged.visits, next(unique_counter), merged))

        merged = degree_two_merge_on_node(edge.end_node)
        if merged is not None and merged.visits < min_visits:
            heappush(to_check, (merged.visits, next(unique_counter), merged))


if __name__ == '__main__':
    tic = time.time()

    simulate = False
    if simulate:
        print("simulating...", flush=True)
        bbox = router.BBox(6.44,42.62,13.41,47.29) # N Italy large
        node_map = router.simulate(n=10000, bbox=bbox)

        print("simulate took", time.time() - tic, "s", flush=True)

        print("merging...", flush=True)
        degree_two_merge(node_map)

        print("writing...", flush=True)
        with open('output.json', 'w') as f:
            json.dump(router.to_geojson(node_map), f)
    
    process = False
    if process:
        print("reading...", flush=True)
        node_map = read('output.json')
        
        min_visits = 100
        filter_by_visits(node_map, min_visits)

        remove_stubs(node_map)
        remove_empty_nodes(node_map)

        print("writing...", flush=True)
        with open('output2.json', 'w') as f:
            json.dump(router.to_geojson(node_map), f)
    
    stefans_data = True
    if stefans_data:
        print("reading stefan's data...", flush=True)
        # node_map = read_stefans_data('traffic_export_sorted.csv')
        # node_map = read_stefans_data('20241219_poi_traffic_tanzania.csv')
        # node_map = read_stefans_data('20241219_poi_traffic_italy.csv')
        node_map = read_stefans_data('20241219_poi_traffic_california.csv')

        print("done reading after ", int(time.time() - tic), 's')

        thresholds = [
            '0', 
            '1e6', '5e6', '1e7', '5e7', '1e8', '5e8', '1e9', '5e9', '1e10'
        ]
        for min_visits_str in thresholds:
            print("processing " + min_visits_str)
            min_visits = float(min_visits_str)
            filter_by_visits(node_map, min_visits)

            if min_visits_str != '0':
                remove_stubs(node_map)
                remove_empty_nodes(node_map)

            print("writing...", flush=True)
            with open(f'output-{min_visits_str}.json', 'w') as f:
                json.dump(router.to_geojson(node_map), f)
            
            print("done with ", min_visits_str, int(time.time() - tic), 's')

    
    print("analyze.py total time", int(time.time() - tic), 's')

