import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.reader.SourceFeature;

import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.LineString;


public class Bretagne implements Profile {
    public Map<Long, Integer> wayIdToSemanticMinZoom = new HashMap<>();
    public Map<Long, Integer> wayIdToRoutingMinZoom = new HashMap<>();

    static LineString linestring(double startLat, double startLon, double endLat, double endLon) {
        var coordinates = new Coordinate[2];
        coordinates[0] = new CoordinateXY(startLon, startLat);
        coordinates[1] = new CoordinateXY(endLon, endLat);
        return GeoUtils.JTS_FACTORY.createLineString(coordinates);
    }

    public static void main(String[] args) {
        var myProfile = new Bretagne();

        String defaultArea = "bretagne";
        System.out.println("get semantic minzoom...");

        myProfile.wayIdToSemanticMinZoom = SemanticMinZoom.getMinZoomMap(args, defaultArea);

        System.out.println("get routing minzoom...");
        myProfile.wayIdToRoutingMinZoom = RoutingMinZoom.getMinZoomMap(myProfile.wayIdToSemanticMinZoom);

        // var arguments = Arguments.fromArgs(args)
        //     .withDefault("download", true)
        //     .withDefault("minzoom", 6)
        //     .withDefault("maxzoom", 12);

        // String area = arguments.getString("area", "geofabrik area to download", defaultArea);
        // Planetiler.create(arguments)
        //     .addOsmSource("osm", Path.of("data", area + ".osm.pbf"), "geofabrik:" + area)
        //     .overwriteOutput(Path.of("data", "test.pmtiles"))
        //     .setProfile(myProfile)
        //     .run();

    }

    @Override
    public void processFeature(SourceFeature sourceFeature, FeatureCollector features) {
        if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway")) {
            if (wayIdToSemanticMinZoom.keySet().contains(sourceFeature.id())) {
                int semanticMinZoom = wayIdToSemanticMinZoom.get(sourceFeature.id());
                if (semanticMinZoom <= 6) {
                    features.line("waterway")
                        .setAttr("semanticMinZoom", semanticMinZoom)
                        .setAttr("highway", sourceFeature.getTag("highway"))
                        .setMinZoom(semanticMinZoom)
                        .setMinPixelSize(0)
                        .setPixelTolerance(0);
                }
            }

            if (wayIdToRoutingMinZoom.keySet().contains(sourceFeature.id())) {
                int routingMinZoom = wayIdToRoutingMinZoom.get(sourceFeature.id());
                features.line("transportation_name")
                    .setAttr("routingMinZoom", routingMinZoom)
                    .setAttr("highway", sourceFeature.getTag("highway"))
                    .setMinZoom(routingMinZoom)
                    .setMinPixelSize(0)
                    .setPixelTolerance(0);  
            }
        }
    }

    @Override
    public String attribution() {
        return OSM_ATTRIBUTION;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }
}