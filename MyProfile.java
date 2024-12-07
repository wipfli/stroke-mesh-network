import java.nio.file.Path;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
// import com.onthegomap.planetiler.util.LoopLineMerger;
import com.onthegomap.planetiler.geo.GeometryType;
import com.onthegomap.planetiler.geo.MutableCoordinateSequence;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MyProfile implements Profile {

    public static List<Coordinate> newCoordinateList(double... coords) {
        List<Coordinate> result = new ArrayList<>(coords.length / 2);
        for (int i = 0; i < coords.length; i += 2) {
            result.add(new CoordinateXY(coords[i], coords[i + 1]));
        }
        return result;
    }

    private Map<String, Long> highwayToGroupId = new HashMap<>();

    MyProfile() {
        long groupId = 0;
        // highwayToGroupId.put("primary_link", groupId++);
        // highwayToGroupId.put("trunk_link", groupId++);
        // highwayToGroupId.put("motorway_link", groupId++);
        highwayToGroupId.put("primary", groupId++);
        // highwayToGroupId.put("trunk", groupId++);
        // highwayToGroupId.put("motorway", groupId++);
    }

    public static void main(String[] args) {
        var arguments = Arguments.fromArgs(args)
                .withDefault("download", true)
                // .withDefault("minzoom", 13)
                .withDefault("maxzoom", 14);
        String area = "italy-north-highways";
        Planetiler.create(arguments)
                .addOsmSource("osm", Path.of("data", area + ".osm.pbf"), "geofabrik:" + area)
                .overwriteOutput(Path.of("output.pmtiles"))
                .setProfile(new MyProfile())
                .run();
    }

    @Override
    public void processFeature(SourceFeature sourceFeature, FeatureCollector features) {
        if (sourceFeature.canBeLine()) {

            boolean keep = false;
            for (var highway : highwayToGroupId.keySet()) {
                keep = keep || sourceFeature.hasTag("highway", highway);
            }

            if (keep) {
                var minzoom = 3;
                if (sourceFeature.hasTag("highway", "trunk", "trunk_link")) {
                    minzoom = 6;
                } else if (sourceFeature.hasTag("highway", "primary")) {
                    minzoom = 6;
                }

                features.line("roads")
                        // .setAttr("idx", sourceFeature.id())
                        .setPixelTolerance(0.0)
                        .setAttr("highway", sourceFeature.getTag("highway"))
                        .setAttr("groupId", highwayToGroupId.get(sourceFeature.getTag("highway").toString()))
                        .setMinZoom(minzoom)
                        .setMinPixelSize(0.0);
            }
        }
    }

    @Override
    public List<VectorTile.Feature> postProcessLayerFeatures(String layer, int zoom,
            List<VectorTile.Feature> items) throws GeometryException {
        List<VectorTile.Feature> result = new ArrayList<>();

        // var feature1 = items.getFirst();
        // System.out.println(feature1);
        // System.exit(0);
        // LoopLineMerger merger = new LoopLineMerger();
        // var feature1 = items.getFirst();
        // var factory = feature1.geometry().decode().getFactory();
        // merger.add(new LoopLineMerger.LineStringWithGroupId(
        //         factory.createLineString(newCoordinateList(0, 0, 1, 0, 1, 1).toArray(new Coordinate[0])), 0));
        // merger.add(new LoopLineMerger.LineStringWithGroupId(
        //         factory.createLineString(newCoordinateList(1, 1, 0, 0).toArray(new Coordinate[0])), 0));
        // System.out.println(merger.getMergedLineStrings());
        // System.exit(0);

        double buffer = 5.0;
        double minLength = 0;
        double loopMinLength = 0 * 5 * 0.0625 * Math.pow(2, zoom - 7);
        double stubMinLength = 0 / Math.pow(2, 14 - zoom);

        double tolerance = -1 * 0.0625;
        boolean mergeStrokes = false;
        boolean debug = true;

        result = mergeLineStrings(items, buffer, minLength, stubMinLength,
            loopMinLength, tolerance, mergeStrokes, debug);

        // tolerance = 1 * 0.0625;
        // mergeStrokes = false;
        // debug = true;

        // result = mergeLineStrings(result, buffer, minLength, stubMinLength,
        //     loopMinLength, tolerance, mergeStrokes, debug);

        // tolerance = -1;
        // mergeStrokes = true;
        // debug = true;
        // result = mergeLineStrings(result, buffer, minLength, stubMinLength,
        //     loopMinLength, tolerance, mergeStrokes, debug);

        // buffer = 4.0;
        // minLength = 0 * 0.0625;
        // stubMinLength = 30 * 0.0625;
        // loopMinLength = 0 * 0.0625;

        // tolerance = -1 * 0.0625;
        // mergeStrokes = false;

        // result = mergeLineStrings(result, buffer, minLength, stubMinLength,
        // loopMinLength, tolerance, mergeStrokes);

        // for (var feature : result) {
        // if (feature.hasTag("highway", "motorway_link")) {
        // feature.setTag("highway", "motorway");
        // feature.setTag("groupId", highwayToGroupId.get("motorway"));
        // }
        // if (feature.hasTag("highway", "trunk_link")) {
        // feature.setTag("highway", "trunk");
        // feature.setTag("groupId", highwayToGroupId.get("trunk"));
        // }
        // }

        // buffer = 4.0;
        // minLength = 0 * 0.0625;
        // stubMinLength = 30 * 0.0625;
        // loopMinLength = 30 * 0.0625;

        // tolerance = 1 * 0.0625;
        // mergeStrokes = false;

        // result = mergeLineStrings(result, buffer, minLength, stubMinLength,
        // loopMinLength, tolerance, mergeStrokes);

        return result;
    }

    public static List<VectorTile.Feature> mergeLineStrings(List<VectorTile.Feature> features, double buffer,
            double minLength, double stubMinLength, double loopMinLength, double tolerance, boolean mergeStrokes, boolean debug) {
        List<VectorTile.Feature> result = new ArrayList<>(features.size());
        List<List<VectorTile.Feature>> groupedByAttrs = new ArrayList<>(
                FeatureMerge.groupByAttrs(features, result, GeometryType.LINE));

        groupedByAttrs.sort((groupA, groupB) -> Long.compare((Long) groupA.getFirst().getTag("groupId"),
                (Long) groupB.getFirst().getTag("groupId")));
        LoopLineMerger merger = new LoopLineMerger()
                .setTolerance(tolerance)
                .setMergeStrokes(mergeStrokes)
                .setMinLength(minLength)
                .setLoopMinLength(loopMinLength)
                .setPrecisionModel(new PrecisionModel(PrecisionModel.FLOATING))
                .setStubMinLength(stubMinLength);

        var groupId = 0;
        for (List<VectorTile.Feature> groupedFeatures : groupedByAttrs) {
            for (VectorTile.Feature feature : groupedFeatures) {
                try {
                    var geometry = feature.geometry().decode();
                    if (geometry instanceof LineString line) {
                        merger.add(new LoopLineMerger.LineStringWithGroupId(line, groupId));
                    }
                } catch (GeometryException e) {
                    e.log("Error decoding vector tile feature for line merge: " + feature);
                }
            }
            groupId++;
        }

        List<LoopLineMerger.LineStringWithGroupId> outputSegments = new ArrayList<>();
        for (var lineWithGroupId : merger.getMergedLineStrings()) {
            if (buffer >= 0) {
                removeDetailOutsideTile(lineWithGroupId, buffer, outputSegments);
            } else {
                outputSegments.add(lineWithGroupId);
            }
        }

        if (!outputSegments.isEmpty()) {
            int debugId = 0;
            for (var outputSegment : outputSegments) {
                Map<String, Object> attrs = new HashMap<>();
                if (debug) {
                    attrs.put("debugId", debugId);
                    attrs.put("length", String.format("%.3f", outputSegment.line().getLength()));
                }
                var feature1 = groupedByAttrs.get(outputSegment.groupId()).getFirst();
                result.add(feature1.copyWithExtraAttrs(attrs).copyWithNewGeometry(outputSegment.line()));
                if (debug) {
                    attrs.put("kind", "start");
                    result.add(
                            feature1.copyWithNewGeometry(outputSegment.line().getStartPoint()).copyWithExtraAttrs(attrs));
                    attrs.put("kind", "end");

                    result.add(feature1.copyWithNewGeometry(outputSegment.line().getEndPoint()).copyWithExtraAttrs(attrs));    
                }
                
                // attrs.remove("kind");
                // attrs.put("apoint", "yes");
                // for (var coordinate : outputSegment.line().getCoordinates()) {
                // var point = outputSegment.line().getFactory().createPoint(coordinate);
                // result.add(feature1.copyWithExtraAttrs(attrs).copyWithNewGeometry(point));
                // }

                debugId++;
            }
        }
        return result;
    }

    private static void removeDetailOutsideTile(LoopLineMerger.LineStringWithGroupId input, double buffer,
            List<LoopLineMerger.LineStringWithGroupId> output) {
        MutableCoordinateSequence current = new MutableCoordinateSequence();
        CoordinateSequence seq = input.line().getCoordinateSequence();
        boolean wasIn = false;
        double min = -buffer, max = 256 + buffer;
        double x = seq.getX(0), y = seq.getY(0);
        Envelope env = new Envelope();
        Envelope outer = new Envelope(min, max, min, max);
        for (int i = 0; i < seq.size() - 1; i++) {
            double nextX = seq.getX(i + 1), nextY = seq.getY(i + 1);
            env.init(x, nextX, y, nextY);
            boolean nowIn = env.intersects(outer);
            if (nowIn || wasIn) {
                current.addPoint(x, y);
            } else { // out
                // wait to flush until 2 consecutive outs
                if (!current.isEmpty()) {
                    if (current.size() >= 2) {
                        output.add(new LoopLineMerger.LineStringWithGroupId(
                                GeoUtils.JTS_FACTORY.createLineString(current), input.groupId()));
                    }
                    current = new MutableCoordinateSequence();
                }
            }
            wasIn = nowIn;
            x = nextX;
            y = nextY;
        }

        // last point
        double lastX = seq.getX(seq.size() - 1), lastY = seq.getY(seq.size() - 1);
        env.init(x, lastX, y, lastY);
        if (env.intersects(outer) || wasIn) {
            current.addPoint(lastX, lastY);
        }

        if (current.size() >= 2) {
            output.add(new LoopLineMerger.LineStringWithGroupId(GeoUtils.JTS_FACTORY.createLineString(current),
                    input.groupId()));
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