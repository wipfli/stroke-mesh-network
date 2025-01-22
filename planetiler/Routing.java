import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Coordinate;

import com.onthegomap.planetiler.geo.GeoUtils;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

public class Routing {

    static LineString linestring(double startLat, double startLon, double endLat, double endLon) {
        var coordinates = new Coordinate[2];
        coordinates[0] = new CoordinateXY(startLon, startLat);
        coordinates[1] = new CoordinateXY(endLon, endLat);
        return GeoUtils.JTS_FACTORY.createLineString(coordinates);
    }

    public static void main(String[] args) {
        var merger = new LoopLineMerger2()
            .setPrecisionModel(new PrecisionModel());
        String filePath = "traffic_export_sorted.csv";
        // String filePath = "traffic.csv";
        // String filePath = "traffic_short.csv";
        // String filePath = "mittelland.csv";

        String line;
        String delimiter = ","; // Adjust as necessary

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

                merger.add(linestring(startLat, startLon, endLat, endLon), visits, wayId);
                // int deadEnd = Integer.parseInt(values[6]);
                // System.out.println(wayId + " " + startLat + " " + startLon);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        merger.setMinVisits(1e9);
        merger.setLoopMinLength(0.001);
        merger.setStubMinLength(0.1);

        for (var linestring : merger.getMergedLineStrings()) {
            System.out.println(linestring);
        }

        // var merger2 = new LoopLineMerger2();
        // for (var mergedLine : merger.getMergedLineStrings()) {
        //     merger2.add(mergedLine, 1, 1);
        // }

        // merger2.setPrecisionModel(new PrecisionModel());
        // // merger2.setLoopMinLength(0.2);
        // // merger2.setStubMinLength(0.1);
        // // merger2.setTolerance(0.002);

        // for (var linestring : merger2.getMergedLineStrings()) {
        //     System.out.println(linestring);
        // }

        // merger.getMergedLineStrings();
        // for (var wayId : merger.getMergedWayIds()) {
        //     System.out.println(wayId);
        // }
    }
}
