package de.tu_berlin.dos.arm.traffic_monitoring.producer;

import de.tu_berlin.dos.arm.traffic_monitoring.common.events.Point;
import de.tu_berlin.dos.arm.traffic_monitoring.common.utils.FileReader;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.io.EdgeProvider;
import org.jgrapht.io.JSONImporter;
import org.jgrapht.io.VertexProvider;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

enum RoutesGraph { GET;

    private Graph<Point, DefaultEdge> graph;

    void importFromResource(String graphFileName) throws Exception {

        File graphFile = FileReader.GET.read(graphFileName, File.class);

        this.graph = new DirectedMultigraph<>(DefaultEdge.class);

        VertexProvider<Point> vp = (id, attributes) -> {
            return new Point(
                Float.parseFloat(attributes.get("latitude").getValue()),
                Float.parseFloat(attributes.get("longitude").getValue()));
        };

        EdgeProvider<Point, DefaultEdge> ep = (from, to, label, attributes) -> {
            return this.graph.getEdgeSupplier().get();
        };

        JSONImporter<Point, DefaultEdge> importer = new JSONImporter<>(vp, ep);
        importer.importGraph(this.graph, graphFile);
    }

    List<Point> getRandomRoute() {

        // use Dijkstra algorithm implementation to find shortest path
        DijkstraShortestPath<Point, DefaultEdge> dijkstraAlg = new DijkstraShortestPath<>(this.graph);
        // retrieve all vertices form the graph
        Set<Point> vertexSet = this.graph.vertexSet();
        // loop until a route is found
        while (true) {
            // select two vertices at random
            Optional<Point> c1 = vertexSet.stream().skip((int)(vertexSet.size() * Math.random())).findFirst();
            Optional<Point> c2 = vertexSet.stream().skip((int)(vertexSet.size() * Math.random())).findFirst();
            // test if selected vertices exist
            if (c1.isPresent() && c2.isPresent()) {
                // if the same point is selected for both, then retry
                if (c1.get().equals(c2.get())) continue;
                // attempt to find the shortest path between two selected vertices
                SingleSourcePaths<Point, DefaultEdge> iPaths = dijkstraAlg.getPaths(c1.get());
                // if there is no connection between these points, then retry
                if (iPaths.getPath(c2.get()) == null) continue;
                // return list of waypoints
                return iPaths.getPath(c2.get()).getVertexList();
            }
        }
    }
}
