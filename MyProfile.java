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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MyProfile implements Profile {
    public static void main(String[] args) {
        var arguments = Arguments.fromArgs(args)
                .withDefault("download", true)
                .withDefault("maxzoom", 7);
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
            if (sourceFeature.hasTag("highway", "motorway", "motorway_link", "trunk", "trunk_link", "primary")) {
                var minzoom = 3;
                if (sourceFeature.hasTag("highway", "trunk", "trunk_link")) {
                    minzoom = 6;
                } else if (sourceFeature.hasTag("highway", "primary")) {
                    minzoom = 7;
                }
                features.line("roads")
                        // .setAttr("idx", sourceFeature.id())
                        .setPixelTolerance(0.0)
                        .setAttr("highway", sourceFeature.getTag("highway"))
                        .setMinZoom(minzoom)
                        .setMinPixelSize(0.0);
            }
        }
    }

    @Override
    public List<VectorTile.Feature> postProcessLayerFeatures(String layer, int zoom,
            List<VectorTile.Feature> items) throws GeometryException {
        List<VectorTile.Feature> result = new ArrayList<>();


        double buffer = 4.0;
        double minLength = 0 * 0.0625;
        double stubMinLength = 0;
        double loopMinLength = 0;

        double tolerance = 1 * 0.0625;
        boolean mergeStrokes = false;

        result = mergeLineStrings(items, buffer, minLength, stubMinLength, loopMinLength, tolerance, mergeStrokes);

        // buffer = 4.0;
        // minLength = 0 * 0.0625;
        // stubMinLength = 8 * 0.0625;
        // loopMinLength = 8 * 0.0625;

        // tolerance = 1 * 0.0625;
        // mergeStrokes = false;

        // result = mergeLineStrings(items, buffer, minLength, stubMinLength, loopMinLength, tolerance, mergeStrokes);


        return result;
    }

    public static List<VectorTile.Feature> mergeLineStrings(List<VectorTile.Feature> features, double buffer, double minLength, double stubMinLength, double loopMinLength, double tolerance, boolean mergeStrokes) {
        List<VectorTile.Feature> result = new ArrayList<>(features.size());
        var groupedByAttrs = FeatureMerge.groupByAttrs(features, result, GeometryType.LINE);
        for (List<VectorTile.Feature> groupedFeatures : groupedByAttrs) {
            VectorTile.Feature feature1 = groupedFeatures.getFirst();

            LoopLineMerger merger = new LoopLineMerger()
                    .setTolerance(tolerance)
                    .setMergeStrokes(mergeStrokes)
                    .setMinLength(minLength)
                    .setLoopMinLength(loopMinLength)
                    // .setPrecisionModel(new PrecisionModel(-0.5))
                    .setStubMinLength(stubMinLength);
            for (VectorTile.Feature feature : groupedFeatures) {
                try {
                    merger.add(feature.geometry().decode());
                } catch (GeometryException e) {
                    e.log("Error decoding vector tile feature for line merge: " + feature);
                }
            }
            List<LineString> outputSegments = new ArrayList<>();
            for (var line : merger.getMergedLineStrings()) {
                if (buffer >= 0) {
                    removeDetailOutsideTile(line, buffer, outputSegments);
                } else {
                    outputSegments.add(line);
                }
            }

            if (!outputSegments.isEmpty()) {
                var idx = 0;
                for (var outputSegment : outputSegments) {
                    Map<String, Object> attrs = new HashMap<>();
                    attrs.put("idx", idx++);
                    result.add(feature1.copyWithNewGeometry(outputSegment).copyWithExtraAttrs(attrs));
                    attrs.put("apoint", "yes");
                    for (var coordinate : outputSegment.getCoordinates()) {
                        result.add(feature1.copyWithNewGeometry(outputSegment.getFactory().createPoint(coordinate)).copyWithExtraAttrs(attrs));
                    }
                    // attrs.put("kind", "start");
                    // result.add(feature1.copyWithNewGeometry(outputSegment.getStartPoint()).copyWithExtraAttrs(attrs));
                    // attrs.put("kind", "end");
                    // result.add(feature1.copyWithNewGeometry(outputSegment.getEndPoint()).copyWithExtraAttrs(attrs));
                }
            }
        }
        return result;
    }

    private static void removeDetailOutsideTile(LineString input, double buffer, List<LineString> output) {
        MutableCoordinateSequence current = new MutableCoordinateSequence();
        CoordinateSequence seq = input.getCoordinateSequence();
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
                        output.add(GeoUtils.JTS_FACTORY.createLineString(current));
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
            output.add(GeoUtils.JTS_FACTORY.createLineString(current));
        }
    }

    private static <G extends Geometry> List<G> sortByHilbertIndex(List<G> geometries) {
        return geometries.stream()
                .map(p -> new WithIndex<>(p, VectorTile.hilbertIndex(p)))
                .sorted(BY_HILBERT_INDEX)
                .map(d -> d.feature)
                .toList();
    }

    private record WithIndex<T>(T feature, int hilbert) {}
    private static final Comparator<WithIndex<?>> BY_HILBERT_INDEX =
    (o1, o2) -> Integer.compare(o1.hilbert, o2.hilbert);

    @Override
    public String attribution() {
        return OSM_ATTRIBUTION;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }
}