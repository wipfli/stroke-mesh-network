def isInBBox(lon, lat, min_lon, min_lat, max_lon, max_lat):
    return (min_lon < lon < max_lon) and (min_lat < lat < max_lat)

def filter(filename):
    # -2.820957,48.474163,-2.779979,48.490708
    min_lon = -2.820957
    min_lat = 48.474163
    max_lon = -2.779979
    max_lat = 48.490708

    with open(filename) as f:
        f.readline() # skip header
        line = f.readline()

        while line != '' and line != '\n':
            numbers = [int(n) for n in line.strip().split(',')]
            way_id, start_lat_orig, start_lon_orig, end_lat_orig, end_lon_orig, visits, deadend = numbers

            start_lat = start_lat_orig / 1e7
            start_lon = start_lon_orig / 1e7
            end_lat = end_lat_orig / 1e7
            end_lon = end_lon_orig / 1e7

            if isInBBox(start_lon, start_lat, min_lon, min_lat, max_lon, max_lat):
                print(','.join([str(n) for n in numbers]))

            line = f.readline()


filter('bretagne_20250122_sorted.csv')
