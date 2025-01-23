import java.nio.file.Path;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.reader.SourceFeature;

import java.util.HashMap;
import java.util.Map;

public class SemanticMinZoom implements Profile {

    final public Map<Long, Integer> wayIdToSemanticMinZoom = new HashMap<>();

    public static Map<Long, Integer> getMinZoomMap(String[] args, String defaultArea) {
        var myProfile = new SemanticMinZoom();

        var arguments = Arguments.fromArgs(args)
            .withDefault("download", true);
        String area = arguments.getString("area", "geofabrik area to download", defaultArea);
        Planetiler.create(arguments)
            .addOsmSource("osm", Path.of("data", area + ".osm.pbf"), "geofabrik:" + area)
            .overwriteOutput(Path.of("data", "test.pmtiles"))
            .setProfile(myProfile)
            .run();
        
        return myProfile.wayIdToSemanticMinZoom;
    }

    @Override
    public void processFeature(SourceFeature sourceFeature, FeatureCollector features) {
        if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway")) {
            
            Map<String, Integer> highwayToMinZoom = new HashMap<>();
            highwayToMinZoom.put("motorway", 3);
            highwayToMinZoom.put("motorway_link", 3);
            highwayToMinZoom.put("trunk", 6);
            highwayToMinZoom.put("trunk_link", 7);
            highwayToMinZoom.put("primary", 7);
            highwayToMinZoom.put("primary_link", 25);
            highwayToMinZoom.put("secondary", 25);
            highwayToMinZoom.put("secondary_link", 25);
            highwayToMinZoom.put("tertiary", 25);
            highwayToMinZoom.put("tertiary_link", 25);
            highwayToMinZoom.put("residential", 25);
            highwayToMinZoom.put("unclassified", 25);
            highwayToMinZoom.put("service", 25);

            var highway = sourceFeature.getTag("highway").toString();

            if (!highwayToMinZoom.keySet().contains(highway)) {
                return;
            }

            int semanticMinZoom = highwayToMinZoom.get(highway);

            if (semanticMinZoom > 15) {
                return;
            }

            // features.line("lines")
            //     .setAttr("semanticMinZoom", semanticMinZoom)
            //     .setAttr("highway", highway)
            //     .setMinZoom(semanticMinZoom)
            //     .setMinPixelSize(0)
            //     .setPixelTolerance(0);
            
            wayIdToSemanticMinZoom.put(sourceFeature.id(), semanticMinZoom);
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