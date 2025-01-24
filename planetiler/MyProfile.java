import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class MyProfile implements Profile {
    final Map<Long, String> wayIdToThreshold = new HashMap<>();

    public static void main(String[] args) {
        var myProfile = new MyProfile();

        List<String> thresholds = List.of("1e8", "5e8", "1e9", "1e10");
        for (var threshold : thresholds) {
            String filePath = "wayIds-" + threshold + ".txt";
            String line;
            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                line = br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    long wayId = Long.parseLong(line);
                    myProfile.wayIdToThreshold.put(wayId, threshold);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        

        var arguments = Arguments.fromArgs(args)
            .withDefault("download", true)
            .withDefault("minzoom", 4)
            .withDefault("maxzoom", 14);
        String area = arguments.getString("area", "geofabrik area to download", "switzerland");
        Planetiler.create(arguments)
            .addOsmSource("osm", Path.of("data", area + ".osm.pbf"), "geofabrik:" + area)
            .overwriteOutput(Path.of("data", "test.pmtiles"))
            .setProfile(myProfile)
            .run();
    }

    @Override
    public void processFeature(SourceFeature sourceFeature, FeatureCollector features) {
        if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway")) {
            // && wayIds.containsKey(sourceFeature.id())) {
            
            Map<String, Integer> highwayToMinZoom = new HashMap<>();
            highwayToMinZoom.put("motorway", 8);
            highwayToMinZoom.put("motorway_link", 9);
            highwayToMinZoom.put("trunk", 8);
            highwayToMinZoom.put("trunk_link", 10);
            highwayToMinZoom.put("primary", 9);
            highwayToMinZoom.put("primary_link", 9);
            highwayToMinZoom.put("secondary", 10);
            highwayToMinZoom.put("secondary_link", 10);
            highwayToMinZoom.put("tertiary", 11);
            highwayToMinZoom.put("tertiary_link", 11);
            highwayToMinZoom.put("residential", 12);
            highwayToMinZoom.put("unclassified", 12);
            highwayToMinZoom.put("service", 13);

            var highway = sourceFeature.getTag("highway").toString();

            if (!highwayToMinZoom.keySet().contains(highway)) {
                return;
            }

            int semanticMinZoom = highwayToMinZoom.get(highway);

            String threshold = "none";
            int visitsMinZoom = 1000;
            if (wayIdToThreshold.containsKey(sourceFeature.id())) {
                threshold = wayIdToThreshold.get(sourceFeature.id());
                Map<String, Integer> thresholdToMinzoomMap = new HashMap<>();
                thresholdToMinzoomMap.put("1e10", 4);
                thresholdToMinzoomMap.put("1e9", 6);
                thresholdToMinzoomMap.put("5e8", 7);
                thresholdToMinzoomMap.put("1e8", 8);
                visitsMinZoom = thresholdToMinzoomMap.get(threshold);
            }
            
            int minZoom = Math.min(visitsMinZoom, semanticMinZoom);

            features.line("lines")
                .setAttr("threshold", threshold)
                .setAttr("visitsMinZoom", visitsMinZoom)
                .setAttr("semanticsMinZoom", semanticMinZoom)
                .setAttr("minZoom", minZoom)
                .setAttr("highway", highway)
                .setAttr("line-width", new MyLineWidth(highway))
                .setMinZoom(minZoom)
                .setMinPixelSize(0)
                .setPixelTolerance(0);
        }
    }

    @Override
    public List<VectorTile.Feature> postProcessLayerFeatures(String layer, int zoom,
            List<VectorTile.Feature> items) throws GeometryException {
        double tolerance = zoom == 14 ? 0 : 8 * 0.0625;
        var result = FeatureMerge.mergeLineStrings(items, 0.0, tolerance, 4.0);
        return result;
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