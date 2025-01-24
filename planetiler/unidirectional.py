# way_id,start_lon,start_lat,end_lon,end_lat,visits,deadend

def parse(file_path, delimiter=','):
    coords_to_visits = {}
    coords_to_wayid = {}
    with open(file_path, 'r') as file:
        next(file)
        
        for line in file:
            values = line.strip().split(delimiter)

            way_id = int(values[0])
            start_lon = int(values[1])
            start_lat = int(values[2])
            end_lon = int(values[3])
            end_lat = int(values[4])
            visits = int(values[5])
            coords = (start_lon, start_lat, end_lon, end_lat)
            reverse_coords = (end_lon, end_lat, start_lon, start_lat)
            if reverse_coords in coords_to_visits:
                coords_to_visits[reverse_coords] += visits
            else:
                coords_to_visits[coords] = visits
                coords_to_wayid[coords] = way_id

        for coords in coords_to_visits:
            print(f'{coords_to_wayid[coords]},{",".join([str(c) for c in coords])},{coords_to_visits[coords]}')
    
file_path = 'bretagne_20250122_sorted.csv'
geojson = parse(file_path)



