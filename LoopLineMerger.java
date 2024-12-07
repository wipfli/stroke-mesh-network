import com.onthegomap.planetiler.geo.DouglasPeuckerSimplifier;
import com.onthegomap.planetiler.geo.GeoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.PriorityQueue;
import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryComponentFilter;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.linemerge.LineMerger;

/**
 * A utility class for merging, simplifying, and connecting linestrings and
 * removing small loops.
 * <p>
 * Compared to JTS {@link LineMerger} which only connects when 2 lines meet at a
 * single point, this utility:
 * <ul>
 * <li>snap-rounds points to a grid
 * <li>splits lines that intersect at a midpoint
 * <li>breaks small loops less than {@code loopMinLength} so only the shortest
 * path connects both endpoints of the loop
 * <li>removes short "hair" edges less than {@code stubMinLength} coming off the
 * side of longer segments
 * <li>simplifies linestrings, without touching the points shared between
 * multiple lines to avoid breaking connections
 * <li>removes any duplicate edges
 * <li>at any remaining 3+ way intersections, connect pairs of edges that form
 * the straightest path through the node
 * <li>remove any remaining edges shorter than {@code minLength}
 * </ul>
 *
 * @see <a href=
 *      "https://oliverwipfli.ch/improving-linestring-merging-in-planetiler-2024-10-30/">Improving
 *      Linestring
 *      Merging in Planetiler</a>
 */
public class LoopLineMerger {
  public record LineStringWithGroupId(LineString line, int groupId) {
  }

  record CoordinatesWithGroupId(List<Coordinate> coordinates, int groupId) {
  }

  private final List<LineStringWithGroupId> input = new ArrayList<>();
  private final List<Node> output = new ArrayList<>();
  private int numNodes = 0;
  private int numEdges = 0;
  private PrecisionModel precisionModel = new PrecisionModel(GeoUtils.TILE_PRECISION);
  private GeometryFactory factory = new GeometryFactory(precisionModel);
  private double minLength = 0.0;
  private double loopMinLength = 0.0;
  private double stubMinLength = 0.0;
  private double tolerance = -1.0;
  private boolean mergeStrokes = false;

  /**
   * Sets the precision model used to snap points to a grid.
   * <p>
   * Use {@link PrecisionModel#FLOATING} to not snap points at all, or
   * {@code new PrecisionModel(4)} to snap to a 0.25px
   * grid.
   */
  public LoopLineMerger setPrecisionModel(PrecisionModel precisionModel) {
    this.precisionModel = precisionModel;
    factory = new GeometryFactory(precisionModel);
    return this;
  }

  /**
   * Sets the minimum length for retaining linestrings in the resulting geometry.
   * <p>
   * Linestrings shorter than this value will be removed. {@code minLength <= 0}
   * disables min length removal.
   */
  public LoopLineMerger setMinLength(double minLength) {
    this.minLength = minLength;
    return this;
  }

  /**
   * Sets the minimum loop length for breaking loops in the merged geometry.
   * <p>
   * Loops that are shorter than loopMinLength are broken up so that only the
   * shortest path between loop endpoints
   * remains. This should be {@code >= minLength}. {@code loopMinLength <= 0}
   * disables loop removal.
   */
  public LoopLineMerger setLoopMinLength(double loopMinLength) {
    this.loopMinLength = loopMinLength;
    return this;
  }

  /**
   * Sets the minimum length of stubs to be removed during processing.
   * <p>
   * Stubs are short "hair" line segments that hang off of a longer linestring
   * without connecting to anything else.
   * {@code stubMinLength <= 0} disables stub removal.
   */
  public LoopLineMerger setStubMinLength(double stubMinLength) {
    this.stubMinLength = stubMinLength;
    return this;
  }

  /**
   * Sets the tolerance for simplifying linestrings during processing. Lines are
   * simplified between endpoints to avoid
   * breaking intersections.
   * <p>
   * {@code tolerance = 0} still removes collinear points, so you need to set
   * {@code tolerance <= 0} to disable
   * simplification.
   */
  public LoopLineMerger setTolerance(double tolerance) {
    this.tolerance = tolerance;
    return this;
  }

  /**
   * Enables or disables stroke merging. Stroke merging connects the straightest
   * pairs of linestrings at junctions with
   * 3 or more attached linestrings based on the angle between them.
   */
  public LoopLineMerger setMergeStrokes(boolean mergeStrokes) {
    this.mergeStrokes = mergeStrokes;
    return this;
  }

  /**
   * Adds a geometry to the merger. Only linestrings from the input geometry are
   * considered.
   */
  public LoopLineMerger add(LineStringWithGroupId lineWithGroupId) {
    input.add(lineWithGroupId);
    return this;
  }

  private void degreeTwoMerge() {
    for (var node : output) {
      degreeTwoMerge(node);
    }
    output.removeIf(node -> node.getEdges().isEmpty());
    assert valid();
  }

  private boolean valid() {
    // when run from a unit test, ensure some basic conditions always hold...
    for (var node : output) {
      for (var edge : node.getEdges()) {
        assert edge.isLoop() || edge.to.getEdges().contains(edge.reversed) : edge.to + " does not contain " +
            edge.reversed;
        for (var other : node.getEdges()) {
          if (edge != other) {
            assert edge != other.reversed : "node contained edge and its reverse " + node;
            assert !edge.coordinates.equals(other.coordinates) : "duplicate edges " + edge + " and " + other;
          }
        }
      }
      assert node.getEdges().size() != 2 || node.getEdges().stream().anyMatch(Edge::isLoop) : "degree 2 node found " +
          node;
    }
    return true;
  }

  private Edge degreeTwoMerge(Node node) {
    if (node.getEdges().size() == 2) {
      Edge a = node.getEdges().getFirst();
      Edge b = node.getEdges().get(1);
      // if one side is a loop, degree is actually > 2
      if (!a.isLoop() && !b.isLoop() && a.groupId == b.groupId) {
        return mergeTwoEdges(node, a, b);
      }
    }
    return null;
  }

  private Edge mergeTwoEdges(Node node, Edge edge1, Edge edge2) {
    // attempt to preserve segment directions from the original line
    // when: A << N -- B then output C reversed from B to A
    // when: A >> N -- B then output C from A to B
    Edge a = edge1.main ? edge2 : edge1;
    Edge b = edge1.main ? edge1 : edge2;
    node.getEdges().remove(a);
    node.getEdges().remove(b);
    List<Coordinate> coordinates = new ArrayList<>();
    coordinates.addAll(a.coordinates.reversed());
    coordinates.addAll(b.coordinates.subList(1, b.coordinates.size()));
    Edge c = new Edge(a.groupId, a.to, b.to, coordinates, a.length + b.length);
    a.to.removeEdge(a.reversed);
    b.to.removeEdge(b.reversed);
    a.to.addEdge(c);
    if (a.to != b.to) {
      b.to.addEdge(c.reversed);
    }
    return c;
  }

  private void strokeMerge() {
    for (var node : output) {
      List<Edge> edges = List.copyOf(node.getEdges());
      if (edges.size() >= 2) {
        record AngledPair(Edge a, Edge b, double angle) {
        }
        List<AngledPair> angledPairs = new ArrayList<>();
        for (var i = 0; i < edges.size(); ++i) {
          var edgei = edges.get(i);
          for (var j = i + 1; j < edges.size(); ++j) {
            var edgej = edges.get(j);
            if (edgei != edgej.reversed) {
              double angle = edgei.angleTo(edgej);
              angledPairs.add(new AngledPair(edgei, edgej, angle));
            }
          }
        }
        angledPairs.sort(Comparator.comparingDouble(angledPair -> angledPair.angle));
        List<Edge> merged = new ArrayList<>();
        for (var angledPair : angledPairs.reversed()) {
          if (merged.contains(angledPair.a) || merged.contains(angledPair.b)
              || angledPair.a.groupId != angledPair.b.groupId) {
            continue;
          }
          mergeTwoEdges(angledPair.a.from, angledPair.a, angledPair.b);
          merged.add(angledPair.a);
          merged.add(angledPair.b);
        }
      }
    }
  }

  private void breakLoops() {
    for (var node : output) {
      if (node.getEdges().size() <= 1) {
        continue;
      }
      for (var current : List.copyOf(node.getEdges())) {
        record HasLoop(Edge edge, double distance) {
        }
        List<HasLoop> loops = new ArrayList<>();
        if (!node.getEdges().contains(current)) {
          continue;
        }
        for (var other : node.getEdges()) {
          double distance = other.length +
              shortestDistanceAStar(other.to, current.to, current.from, loopMinLength - other.length);
          if (distance <= loopMinLength) {
            loops.add(new HasLoop(other, distance));
          }
        }
        if (loops.size() > 1) {
          loops.sort(Comparator.comparingInt((HasLoop l) -> l.edge.groupId)
              .thenComparingDouble((HasLoop l) -> -l.distance));
          Collections.reverse(loops);
          for (var loop : loops.subList(1, loops.size())) {
            loop.edge.remove();
          }
        }
      }
    }
  }

  private double shortestDistanceAStar(Node start, Node end, Node exclude, double maxLength) {
    Map<Integer, Double> bestDistance = new HashMap<>();
    record Candidate(Node node, double length, double minTotalLength) {
    }
    PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingDouble(Candidate::minTotalLength));
    if (exclude != start) {
      frontier.offer(new Candidate(start, 0, start.distance(end)));
    }
    while (!frontier.isEmpty()) {
      Candidate candidate = frontier.poll();
      Node current = candidate.node;
      if (current == end) {
        return candidate.length;
      }

      for (var edge : current.getEdges()) {
        var neighbor = edge.to;
        if (neighbor != exclude) {
          double newDist = candidate.length + edge.length;
          double prev = bestDistance.getOrDefault(neighbor.id, Double.POSITIVE_INFINITY);
          if (newDist < prev) {
            bestDistance.put(neighbor.id, newDist);
            double minTotalLength = newDist + neighbor.distance(end);
            if (minTotalLength <= maxLength) {
              frontier.offer(new Candidate(neighbor, newDist, minTotalLength));
            }
          }
        }
      }
    }
    return Double.POSITIVE_INFINITY;
  }

  private List<List<Edge>> getRightTurnLoops() {
    List<List<Edge>> result = new ArrayList<>();
    Set<Edge> visited = new HashSet<>();
    for (var node : output) {
      for (var edge : node.getEdges()) {
        if (visited.contains(edge)) {
          continue;
        }
        var loop = getRightTurnLoop(edge);
        if (loop.size() > 0) {
          result.add(loop);
          for (var visitedEdge : loop) {
            visited.add(visitedEdge);
          }
        }
      }
    }
    result.sort(Comparator.comparingDouble(loop -> loop.stream().mapToDouble(edge -> edge.length).sum()));
    return result;
  }

  private List<Edge> getRightTurnLoop(Edge startEdge) {
    if (startEdge.from.getEdges().size() == 1 && startEdge.to.getEdges().size() == 1) {
      return new ArrayList<>();
    }

    Edge currentEdge = startEdge;
    List<Edge> result = new ArrayList<>();
    int MAX_DEPTH = 100;
    int depth = 0;
    while (true) {
      result.add(currentEdge);
      List<Edge> nextEdges = currentEdge.to.getEdges();
      int index = nextEdges.indexOf(currentEdge.reversed);
      if (index == nextEdges.size() - 1) {
        currentEdge = nextEdges.getFirst();
      }
      else {
        currentEdge = nextEdges.get(index + 1);
      }
      if (currentEdge.equals(startEdge)) {
        break;
      }
      if (depth > MAX_DEPTH) {
        return new ArrayList<>();
      }
      depth++;
    }
    if (loopMinLength > 0 && result.stream().mapToDouble(edge -> edge.length).sum() > loopMinLength) {
      return new ArrayList<>();
    }
    return result;
  }

  private List<List<Edge>> groupByEndpoints(double maxDistance) {
    List<List<Edge>> result = new ArrayList<>();
    List<Edge> allEdges = new ArrayList<>();
    for (var node : output) {
      for (var edge : node.getEdges()) {
        allEdges.add(edge);
      }
    }
    for (int i = 0; i < allEdges.size(); ++i) {
      var edge = allEdges.get(i);
      List<Edge> group = new ArrayList<>();
      group.add(edge);
      for (int j = i + 1; j < allEdges.size(); ++j) {
        var other = allEdges.get(j);
        if (edge.from.distance(other.from) < maxDistance && edge.to.distance(other.to) < maxDistance) {
          group.add(other);
        }
      }
      if (group.size() > 1) {
        result.add(group);
      }
    }
    return result;
  }

  private void removeShortStubEdges() {
    PriorityQueue<Edge> toCheck = new PriorityQueue<>(Comparator.comparingDouble(Edge::length));
    for (var node : output) {
      for (var edge : node.getEdges()) {
        if (isShortStubEdge(edge)) {
          toCheck.offer(edge);
        }
      }
    }
    while (!toCheck.isEmpty()) {
      var edge = toCheck.poll();
      if (edge.removed) {
        continue;
      }
      if (isShortStubEdge(edge)) {
        edge.remove();
      }
      if (degreeTwoMerge(edge.from) instanceof Edge merged) {
        toCheck.offer(merged);
      }
      if (edge.from.getEdges().size() == 1) {
        var other = edge.from.getEdges().getFirst();
        if (isShortStubEdge(other)) {
          toCheck.offer(other);
        }
      }
      if (edge.from != edge.to) {
        if (degreeTwoMerge(edge.to) instanceof Edge merged) {
          toCheck.offer(merged);
        }
        if (edge.to.getEdges().size() == 1) {
          var other = edge.to.getEdges().getFirst();
          if (isShortStubEdge(other)) {
            toCheck.offer(other);
          }
        }
      }
    }
  }

  private boolean isShortStubEdge(Edge edge) {
    return edge != null && !edge.removed && edge.length < stubMinLength &&
        (edge.from.getEdges().size() == 1 || edge.to.getEdges().size() == 1 || edge.isLoop());
  }

  private void removeShortEdges() {
    for (var node : output) {
      for (var edge : List.copyOf(node.getEdges())) {
        if (edge.length < minLength) {
          edge.remove();
        }
      }
    }
  }

  private void simplify() {
    List<Edge> toRemove = new ArrayList<>();
    for (var node : output) {
      for (var edge : node.getEdges()) {
        if (edge.main) {
          edge.simplify();
          if (edge.isCollapsed()) {
            toRemove.add(edge);
          }
        }
      }
    }
    toRemove.forEach(Edge::remove);
  }

  private void removeDuplicatedEdges() {
    for (var node : output) {
      List<Edge> toRemove = new ArrayList<>();
      for (var i = 0; i < node.getEdges().size(); ++i) {
        Edge a = node.getEdges().get(i);
        for (var j = i + 1; j < node.getEdges().size(); ++j) {
          Edge b = node.getEdges().get(j);
          if (a.groupId == b.groupId && b.to == a.to && a.coordinates.equals(b.coordinates)) {
            toRemove.add(b);
          }
        }
      }
      for (var edge : toRemove) {
        edge.remove();
      }
    }
  }

  /**
   * Processes the added geometries and returns the merged linestrings.
   * <p>
   * Can be called more than once.
   */
  public List<LineStringWithGroupId> getMergedLineStrings() {
    output.clear();
    var edges = nodeLines(input);
    buildNodes(edges);

    degreeTwoMerge();

    // if (loopMinLength > 0.0) {
    //   breakLoops();
    //   degreeTwoMerge();
    // }

    // if (stubMinLength > 0.0) {
    //   removeShortStubEdges();
    //   // removeShortStubEdges does degreeTwoMerge internally
    // }

    if (tolerance >= 0.0) {
      simplify();
      removeDuplicatedEdges();
      degreeTwoMerge();
    }

    // if (mergeStrokes) {
    //   strokeMerge();
    //   degreeTwoMerge();
    // }

    if (minLength > 0) {
      removeShortEdges();
    }

    List<LineStringWithGroupId> result = new ArrayList<>();

    // for (var loop : loops) {
    //   System.out.println("");
    //   for (var edge : loop) {
    //     System.out.println(edge.id + (edge.main ? "" : "(R)") + " from=" + edge.from.id + " to=" + edge.to.id + " angle=" + edge.angle);
    //     result.add(new LineStringWithGroupId(factory.createLineString(edge.coordinates.toArray(Coordinate[]::new)),
    //           edge.groupId));
    //   }
    // }

    if (mergeStrokes) {
      var loops = getRightTurnLoops();
      for (var loop : loops) {
        loop.getFirst().remove();
      }
      degreeTwoMerge();

      if (stubMinLength > 0.0) {
        removeShortStubEdges();
        // removeShortStubEdges does degreeTwoMerge internally
      }
    }

    // var groupedByEndpoints = groupByEndpoints(loopMinLength);
    // System.out.println("groupedByEndpoints..." + groupedByEndpoints);
    // for (var group : groupedByEndpoints) {
    //   for (var edge : group.subList(1, group.size())) {
    //     // edge.remove();
    //   }
    // }
    var count = 0;
    for (var node : output) {
      for (var edge : node.getEdges()) {
        // System.out.println(edge.id + (edge.main ? "" : "(R)") + " from=" + edge.from.id + " to=" + edge.to.id + " angle=" + edge.angle);
        if (edge.main) {
          result.add(new LineStringWithGroupId(factory.createLineString(edge.coordinates.toArray(Coordinate[]::new)),
              edge.groupId));
          count++;
        }
      }
    }
    System.out.println("count=" + count);

    return result;
  }

  private static double length(List<Coordinate> edge) {
    Coordinate last = null;
    double length = 0;
    for (Coordinate coord : edge) {
      if (last != null) {
        length += last.distance(coord);
      }
      last = coord;
    }
    return length;
  }

  private void buildNodes(List<CoordinatesWithGroupId> edges) {
    Map<Coordinate, Node> nodes = new HashMap<>();
    for (var coordinatesWithGroupId : edges) {
      var coordinateSequence = coordinatesWithGroupId.coordinates;
      Coordinate first = coordinateSequence.getFirst();
      Node firstNode = nodes.get(first);
      if (firstNode == null) {
        firstNode = new Node(first);
        nodes.put(first, firstNode);
        output.add(firstNode);
      }

      Coordinate last = coordinateSequence.getLast();
      Node lastNode = nodes.get(last);
      if (lastNode == null) {
        lastNode = new Node(last);
        nodes.put(last, lastNode);
        output.add(lastNode);
      }

      double length = length(coordinateSequence);

      Edge edge = new Edge(coordinatesWithGroupId.groupId(), firstNode, lastNode, coordinateSequence, length);

      firstNode.addEdge(edge);
      if (firstNode != lastNode) {
        lastNode.addEdge(edge.reversed);
      }
    }
  }

  private List<CoordinatesWithGroupId> nodeLines(List<LineStringWithGroupId> input) {
    Map<Coordinate, Integer> nodeCounts = new HashMap<>();
    List<CoordinatesWithGroupId> coords = new ArrayList<>(input.size());
    for (var lineWithGroupId : input) {
      var line = lineWithGroupId.line();
      var coordinateSequence = line.getCoordinateSequence();
      List<Coordinate> snapped = new ArrayList<>();
      Coordinate last = null;
      for (int i = 0; i < coordinateSequence.size(); i++) {
        Coordinate current = new CoordinateXY(coordinateSequence.getX(i), coordinateSequence.getY(i));
        precisionModel.makePrecise(current);
        if (last == null || !last.equals(current)) {
          snapped.add(current);
          nodeCounts.merge(current, 1, Integer::sum);
        }
        last = current;
      }
      if (snapped.size() >= 2) {
        coords.add(new CoordinatesWithGroupId(snapped, lineWithGroupId.groupId()));
      }
    }

    List<CoordinatesWithGroupId> result = new ArrayList<>(input.size());
    for (var coordinatesWithGroupId : coords) {
      var coordinateSequence = coordinatesWithGroupId.coordinates();
      int start = 0;
      for (int i = 0; i < coordinateSequence.size(); i++) {
        Coordinate coordinate = coordinateSequence.get(i);
        if (i > 0 && i < coordinateSequence.size() - 1 && nodeCounts.get(coordinate) > 1) {
          result.add(
              new CoordinatesWithGroupId(coordinateSequence.subList(start, i + 1), coordinatesWithGroupId.groupId()));
          start = i;
        }
      }
      if (start < coordinateSequence.size()) {
        var sublist = start == 0 ? coordinateSequence : coordinateSequence.subList(start, coordinateSequence.size());
        result.add(new CoordinatesWithGroupId(sublist, coordinatesWithGroupId.groupId()));
      }
    }
    return result;
  }

  private class Node {
    final int id = numNodes++;
    final List<Edge> edge = new ArrayList<>();
    Coordinate coordinate;

    Node(Coordinate coordinate) {
      this.coordinate = coordinate;
    }

    void addEdge(Edge edge) {
      for (Edge other : this.edge) {
        if (other.groupId == edge.groupId && other.coordinates.equals(edge.coordinates)) {
          return;
        }
      }
      this.edge.add(edge);
      this.edge.sort((e1, e2) -> Double.compare(e1.angle, e2.angle));
    }

    List<Edge> getEdges() {
      return edge;
    }

    void removeEdge(Edge edge) {
      this.edge.remove(edge);
    }

    @Override
    public String toString() {
      return "Node{" + id + ": " + edge + '}';
    }

    double distance(Node end) {
      return coordinate.distance(end.coordinate);
    }
  }

  private class Edge {

    final int id;
    final int groupId;
    final Node from;
    final Node to;
    final double length;
    final boolean main;
    boolean removed;
    final double angle;

    Edge reversed;
    List<Coordinate> coordinates;

    private Edge(int groupId, Node from, Node to, List<Coordinate> coordinateSequence, double length) {
      this(numEdges, groupId, from, to, length, coordinateSequence, true, null);
      reversed = new Edge(numEdges, groupId, to, from, length, coordinateSequence.reversed(), false, this);
      numEdges++;
    }

    private Edge(int id, int groupId, Node from, Node to, double length, List<Coordinate> coordinates, boolean main,
        Edge reversed) {
      this.id = id;
      this.groupId = groupId;
      this.from = from;
      this.to = to;
      this.length = length;
      this.coordinates = coordinates;
      this.main = main;
      this.reversed = reversed;
      assert coordinates.size() >= 2;
      angle = Angle.angle(coordinates.get(0), coordinates.get(1));
    }

    void remove() {
      if (!removed) {
        from.removeEdge(this);
        to.removeEdge(reversed);
        removed = true;
      }
    }

    double angleTo(Edge other) {
      assert from.equals(other.from);
      return Math.abs(Angle.normalize(angle - other.angle));
    }

    double length() {
      return length;
    }

    void simplify() {
      coordinates = DouglasPeuckerSimplifier.simplify(coordinates, tolerance, false);
      if (reversed != null) {
        reversed.coordinates = coordinates.reversed();
      }
    }

    boolean isCollapsed() {
      return coordinates.size() < 2 ||
          (coordinates.size() == 2 && coordinates.getFirst().equals(coordinates.getLast()));
    }

    boolean isLoop() {
      return from == to;
    }

    @Override
    public String toString() {
      return "Edge{" + from.id + "->" + to.id + (main ? "" : "(R)") + ": [" + coordinates.getFirst() + ".." +
          coordinates.getLast() + "], length=" + length + ", groupId=" + groupId + ", angle=" + angle + '}';
    }
  }
}
