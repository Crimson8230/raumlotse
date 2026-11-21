package at.mci.igp.raumlotse.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

/** Dijkstra over a {@link RoutingGraph}; the cost is walking time in seconds. */
public final class RouteCalculator {

    public record Path(double meters, double seconds, List<UUID> nodes, List<RoutingGraph.Edge> edges) {
    }

    /** Shortest-path tree from one source; unreachable nodes are absent. */
    public static final class Tree {
        private final Map<UUID, Double> meters = new HashMap<>();
        private final Map<UUID, Double> seconds = new HashMap<>();
        private final Map<UUID, UUID> firstHop = new HashMap<>();

        public boolean reachable(UUID node) {
            return seconds.containsKey(node);
        }

        public double meters(UUID node) {
            return meters.get(node);
        }

        public double seconds(UUID node) {
            return seconds.get(node);
        }

        /** First node after the source on the shortest path; null for the source itself. */
        public UUID firstHop(UUID node) {
            return firstHop.get(node);
        }
    }

    private record Entry(UUID node, double seconds) {
    }

    private RouteCalculator() {
    }

    /**
     * Shortest tree from {@code source}. With {@code restrictToMap} only walking edges on that map are followed
     * (no floor changes).
     */
    public static Tree tree(RoutingGraph graph, UUID source, UUID restrictToMap) {
        Tree tree = new Tree();
        if (graph.node(source) == null) {
            return tree;
        }
        PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.seconds(), b.seconds()));
        tree.seconds.put(source, 0.0);
        tree.meters.put(source, 0.0);
        queue.add(new Entry(source, 0.0));
        java.util.Set<UUID> done = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            if (!done.add(entry.node())) {
                continue;
            }
            for (RoutingGraph.Edge edge : graph.edgesFrom(entry.node())) {
                if (restrictToMap != null && edge.connection() != null) {
                    continue;
                }
                double candidate = entry.seconds() + edge.seconds();
                Double known = tree.seconds.get(edge.to());
                if (known == null || candidate < known - 1e-12) {
                    tree.seconds.put(edge.to(), candidate);
                    tree.meters.put(edge.to(), tree.meters.get(entry.node()) + edge.meters());
                    tree.firstHop.put(edge.to(), entry.node().equals(source) ? edge.to() : tree.firstHop.get(entry.node()));
                    queue.add(new Entry(edge.to(), candidate));
                }
            }
        }
        return tree;
    }

    public static Optional<Path> shortest(RoutingGraph graph, UUID from, UUID to) {
        if (graph.node(from) == null || graph.node(to) == null) {
            return Optional.empty();
        }
        Map<UUID, Double> seconds = new HashMap<>();
        Map<UUID, UUID> previous = new HashMap<>();
        Map<UUID, RoutingGraph.Edge> previousEdge = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.seconds(), b.seconds()));
        seconds.put(from, 0.0);
        queue.add(new Entry(from, 0.0));
        java.util.Set<UUID> done = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            if (!done.add(entry.node())) {
                continue;
            }
            if (entry.node().equals(to)) {
                break;
            }
            for (RoutingGraph.Edge edge : graph.edgesFrom(entry.node())) {
                double candidate = entry.seconds() + edge.seconds();
                Double known = seconds.get(edge.to());
                if (known == null || candidate < known - 1e-12) {
                    seconds.put(edge.to(), candidate);
                    previous.put(edge.to(), entry.node());
                    previousEdge.put(edge.to(), edge);
                    queue.add(new Entry(edge.to(), candidate));
                }
            }
        }
        if (!seconds.containsKey(to)) {
            return Optional.empty();
        }
        List<UUID> nodes = new ArrayList<>();
        List<RoutingGraph.Edge> path = new ArrayList<>();
        double meters = 0.0;
        for (UUID at = to; at != null; at = previous.get(at)) {
            nodes.add(at);
            RoutingGraph.Edge edge = previousEdge.get(at);
            if (edge != null) {
                path.add(edge);
                meters += edge.meters();
            }
        }
        Collections.reverse(nodes);
        Collections.reverse(path);
        return Optional.of(new Path(meters, seconds.get(to), List.copyOf(nodes), List.copyOf(path)));
    }
}
