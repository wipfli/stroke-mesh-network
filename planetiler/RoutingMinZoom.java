import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import com.onthegomap.planetiler.geo.GeoUtils;

import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

public class RoutingMinZoom {

    static LineString linestring(double startLat, double startLon, double endLat, double endLon) {
        var coordinates = new Coordinate[2];
        coordinates[0] = new CoordinateXY(startLon, startLat);
        coordinates[1] = new CoordinateXY(endLon, endLat);
        return GeoUtils.JTS_FACTORY.createLineString(coordinates);
    }

    public static Map<Long, Integer> getMinZoomMap(Map<Long, Integer> wayIdToSemanticMinZoom) {

        var merger = new LoopLineMerger4()
            .setPrecisionModel(new PrecisionModel());
        String filePath = "bretagne_20250122_sorted.csv";

        String line;
        String delimiter = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] values = line.split(delimiter);

                long wayId = Long.parseLong(values[0]);
                double startLat = Double.parseDouble(values[1]) / 1e7;
                double startLon = Double.parseDouble(values[2]) / 1e7;
                double endLat = Double.parseDouble(values[3]) / 1e7;
                double endLon = Double.parseDouble(values[4]) / 1e7;
                long visits = Long.parseLong(values[5]);
                int minZoom = wayIdToSemanticMinZoom.containsKey(wayId) ? wayIdToSemanticMinZoom.get(wayId) : 25;
                merger.add(linestring(startLat, startLon, endLat, endLon), visits, minZoom, wayId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new HashMap<>();
        // merger.setMinVisits(1e9);
        // merger.setLoopMinLength(0.001);
        // merger.setStubMinLength(0.1);

        // for (var linestring : merger.getMergedLineStrings()) {
        //     System.out.println(linestring);
        // }
    }
}