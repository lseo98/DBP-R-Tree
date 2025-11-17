package org.dfpl.dbp.rtree;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class RTreeImpl implements RTree {

    private static final int MAX_ENTRIES = 4;

    private static class Node {
        boolean leaf;
        List<Point> points = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        double minX, minY, maxX, maxY;

        Node(boolean leaf) {
            this.leaf = leaf;
        }

        void recalcBounds() {
            if (leaf) {
                recalcFromPoints();
            } else {
                recalcFromChildren();
            }
        }

        private void recalcFromPoints() {
            if (points.isEmpty()) {
                minX = minY = maxX = maxY = 0;
                return;
            }
            Point first = points.get(0);
            minX = maxX = first.getX();
            minY = maxY = first.getY();
            for (Point point : points) {
                minX = Math.min(minX, point.getX());
                maxX = Math.max(maxX, point.getX());
                minY = Math.min(minY, point.getY());
                maxY = Math.max(maxY, point.getY());
            }
        }

        private void recalcFromChildren() {
            if (children.isEmpty()) {
                minX = minY = maxX = maxY = 0;
                return;
            }
            Node first = children.get(0);
            minX = first.minX;
            maxX = first.maxX;
            minY = first.minY;
            maxY = first.maxY;
            for (Node child : children) {
                minX = Math.min(minX, child.minX);
                maxX = Math.max(maxX, child.maxX);
                minY = Math.min(minY, child.minY);
                maxY = Math.max(maxY, child.maxY);
            }
        }
    }

    private Node root;
    private Map<String, Point> pointIndex = new LinkedHashMap<>();
    private final boolean visualizationEnabled;
    private final RTreeVisualizer visualizer;

    public RTreeImpl() {
        this(!GraphicsEnvironment.isHeadless(), 180);
    }

    public RTreeImpl(boolean enableVisualization) {
        this(enableVisualization, 180);
    }

    public RTreeImpl(boolean enableVisualization, int visualizationDelayMs) {
        this.visualizationEnabled = enableVisualization && !GraphicsEnvironment.isHeadless();
        int delay = Math.max(0, visualizationDelayMs);
        this.visualizer = this.visualizationEnabled ? new RTreeVisualizer(delay) : null;
    }

    @Override
    public void add(Point point) {
        String key = pointKey(point);
        if (pointIndex.containsKey(key)) {
            return;
        }
        pointIndex.put(key, point);
        insertInternal(point);
        visualizeInsertion(point);
    }

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        if (root == null || rectangle == null) {
            return Collections.emptyIterator();
        }
        double[] bounds = normalize(rectangle);
        List<Point> result = new ArrayList<>();
        List<TraceEvent> trace = new ArrayList<>();
        search(root, result, bounds[0], bounds[1], bounds[2], bounds[3], trace);
        visualizeSearch(bounds, trace, result);
        search(root, result, bounds[0], bounds[1], bounds[2], bounds[3]);
        return result.iterator();
    }

    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        if (source == null || maxCount <= 0) {
            return Collections.emptyIterator();
        }
        List<Point> allPoints = new ArrayList<>(pointIndex.values());
        allPoints.sort(Comparator.comparingDouble(source::distance));
        int limit = Math.min(maxCount, allPoints.size());
        List<Point> nearestPoints = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Point candidate = allPoints.get(i);
            nearestPoints.add(candidate);
            visualizeNearestStep(source, nearestPoints, candidate, i + 1);
        }
        return nearestPoints.iterator();
        return allPoints.subList(0, limit).iterator();
    }

    @Override
    public void delete(Point point) {
        if (point == null) {
            return;
        }
        String key = pointKey(point);
        if (!pointIndex.containsKey(key)) {
            return;
        }
        pointIndex.remove(key);
        rebuildTree();
        visualizeDeletion(point);
    }

    @Override
    public boolean isEmpty() {
        return pointIndex.isEmpty();
    }

    private void insertInternal(Point point) {
        if (root == null) {
            root = new Node(true);
            root.points.add(point);
            root.recalcBounds();
            return;
        }
        Node sibling = insert(root, point);
        if (sibling != null) {
            Node newRoot = new Node(false);
            newRoot.children.add(root);
            newRoot.children.add(sibling);
            newRoot.recalcBounds();
            root = newRoot;
        }
    }

    private Node insert(Node node, Point point) {
        if (node.leaf) {
            node.points.add(point);
            node.recalcBounds();
            if (node.points.size() > MAX_ENTRIES) {
                return splitLeaf(node);
            }
            return null;
        }
        Node child = chooseSubtree(node, point);
        Node sibling = insert(child, point);
        if (sibling != null) {
            node.children.add(sibling);
        }
        node.recalcBounds();
        if (node.children.size() > MAX_ENTRIES) {
            return splitInternal(node);
        }
        return null;
    }

    private Node chooseSubtree(Node node, Point point) {
        Node bestChild = null;
        double bestEnlargement = Double.MAX_VALUE;
        double bestArea = Double.MAX_VALUE;
        for (Node child : node.children) {
            double enlargement = enlargement(child, point);
            double area = area(child);
            if (enlargement < bestEnlargement || (enlargement == bestEnlargement && area < bestArea)) {
                bestEnlargement = enlargement;
                bestArea = area;
                bestChild = child;
            }
        }
        return bestChild;
    }

    private double area(Node node) {
        return Math.max(0, (node.maxX - node.minX) * (node.maxY - node.minY));
    }

    private double enlargement(Node node, Point point) {
        double newMinX = Math.min(node.minX, point.getX());
        double newMaxX = Math.max(node.maxX, point.getX());
        double newMinY = Math.min(node.minY, point.getY());
        double newMaxY = Math.max(node.maxY, point.getY());
        double newArea = Math.max(0, (newMaxX - newMinX) * (newMaxY - newMinY));
        return newArea - area(node);
    }

    private Node splitLeaf(Node node) {
        List<Point> sorted = new ArrayList<>(node.points);
        sorted.sort(Comparator.comparingDouble(Point::getX));
        int mid = sorted.size() / 2;
        node.points.clear();
        Node sibling = new Node(true);
        for (int i = 0; i < sorted.size(); i++) {
            if (i < mid) {
                node.points.add(sorted.get(i));
            } else {
                sibling.points.add(sorted.get(i));
            }
        }
        node.recalcBounds();
        sibling.recalcBounds();
        return sibling;
    }

    private Node splitInternal(Node node) {
        List<Node> sorted = new ArrayList<>(node.children);
        sorted.sort(Comparator.comparingDouble(this::centerX));
        int mid = sorted.size() / 2;
        node.children.clear();
        Node sibling = new Node(false);
        for (int i = 0; i < sorted.size(); i++) {
            if (i < mid) {
                node.children.add(sorted.get(i));
            } else {
                sibling.children.add(sorted.get(i));
            }
        }
        node.recalcBounds();
        sibling.recalcBounds();
        return sibling;
    }

    private double centerX(Node node) {
        return (node.minX + node.maxX) / 2.0;
    }

    private void search(Node node, List<Point> result, double minX, double minY, double maxX, double maxY,
            List<TraceEvent> trace) {
        if (!intersects(node, minX, minY, maxX, maxY)) {
            trace.add(new TraceEvent(TraceEventType.PRUNED, node));
            return;
        }
        trace.add(new TraceEvent(TraceEventType.VISITED, node));
    private void search(Node node, List<Point> result, double minX, double minY, double maxX, double maxY) {
        if (node.leaf) {
            for (Point point : node.points) {
                if (containsPoint(point, minX, minY, maxX, maxY)) {
                    result.add(point);
                }
            }
            return;
        }
        for (Node child : node.children) {
            search(child, result, minX, minY, maxX, maxY, trace);
            if (intersects(child, minX, minY, maxX, maxY)) {
                search(child, result, minX, minY, maxX, maxY);
            }
        }
    }

    private boolean containsPoint(Point point, double minX, double minY, double maxX, double maxY) {
        return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY;
    }

    private boolean intersects(Node node, double minX, double minY, double maxX, double maxY) {
        return !(node.maxX < minX || node.minX > maxX || node.maxY < minY || node.minY > maxY);
    }

    private double[] normalize(Rectangle rectangle) {
        double x1 = rectangle.getLeftTop().getX();
        double y1 = rectangle.getLeftTop().getY();
        double x2 = rectangle.getRightBottom().getX();
        double y2 = rectangle.getRightBottom().getY();
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        return new double[] { minX, minY, maxX, maxY };
    }

    private String pointKey(Point point) {
        long xBits = Double.doubleToLongBits(point.getX());
        long yBits = Double.doubleToLongBits(point.getY());
        return xBits + ":" + yBits;
    }

    private void rebuildTree() {
        root = null;
        for (Point value : pointIndex.values()) {
            insertInternal(value);
        }
    }

    private void visualizeInsertion(Point point) {
        if (!visualizationEnabled) {
            return;
        }
        List<Point> highlights = new ArrayList<>();
        highlights.add(point);
        visualizer.update(root, "Inserted " + point, null, Collections.emptyList(), Collections.emptyList(), highlights,
                null, null, point);
    }

    private void visualizeDeletion(Point point) {
        if (!visualizationEnabled) {
            return;
        }
        visualizer.update(root, "Deleted " + point, null, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, null, null);
    }

    private void visualizeSearch(double[] bounds, List<TraceEvent> trace, List<Point> resultPoints) {
        if (!visualizationEnabled) {
            return;
        }
        List<Node> visitedSoFar = new ArrayList<>();
        List<Node> prunedSoFar = new ArrayList<>();
        for (TraceEvent event : trace) {
            if (event.type == TraceEventType.VISITED) {
                visitedSoFar.add(event.node);
            } else {
                prunedSoFar.add(event.node);
            }
            visualizer.update(root, "Search rectangle", bounds, new ArrayList<>(visitedSoFar),
                    new ArrayList<>(prunedSoFar), new ArrayList<>(resultPoints), null, null, null);
        }
    }

    private void visualizeNearestStep(Point source, List<Point> nearestPoints, Point currentCandidate, int step) {
        if (!visualizationEnabled) {
            return;
        }
        double radius = source.distance(currentCandidate);
        double[] circleBounds = new double[] { source.getX() - radius, source.getY() - radius, source.getX() + radius,
                source.getY() + radius };
        visualizer.update(root, "KNN step " + step, circleBounds, Collections.emptyList(), Collections.emptyList(),
                new ArrayList<>(nearestPoints), source, radius, currentCandidate);
    }

    private enum TraceEventType {
        VISITED,
        PRUNED
    }

    private static class TraceEvent {
        final TraceEventType type;
        final Node node;

        TraceEvent(TraceEventType type, Node node) {
            this.type = type;
            this.node = node;
        }
    }

    private static class RTreeVisualizer extends JPanel {
        private static final Color CANVAS_BG = Color.WHITE;
        private static final Color NODE_COLOR = new Color(76, 175, 80);
        private static final Color LEAF_COLOR = new Color(33, 150, 243);
        private static final Color PRUNED_COLOR = new Color(244, 67, 54);
        private static final Color VISITED_COLOR = new Color(255, 193, 7);
        private static final Color POINT_COLOR = new Color(66, 66, 66);
        private static final Color HIGHLIGHT_POINT_COLOR = new Color(156, 39, 176);

        private Node root;
        private double[] queryRect;
        private List<Node> visitedNodes = Collections.emptyList();
        private List<Node> prunedNodes = Collections.emptyList();
        private List<Point> highlightPoints = Collections.emptyList();
        private Point nearestSource;
        private Double nearestRadius;
        private Point currentCandidate;
        private String message = "";
        private final JFrame frame;
        private final int delayMs;

        RTreeVisualizer(int delayMs) {
            this.delayMs = delayMs;
            setPreferredSize(new Dimension(700, 700));
            frame = new JFrame("4-way R-Tree Visualizer");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.add(this);
            frame.pack();
            frame.setVisible(true);
        }

        void update(Node root, String message, double[] queryRect, List<Node> visitedNodes, List<Node> prunedNodes,
                List<Point> highlightPoints, Point nearestSource, Double nearestRadius, Point currentCandidate) {
            Runnable task = () -> {
                this.root = root;
                this.message = message == null ? "" : message;
                this.queryRect = queryRect;
                this.visitedNodes = visitedNodes == null ? Collections.emptyList() : new ArrayList<>(visitedNodes);
                this.prunedNodes = prunedNodes == null ? Collections.emptyList() : new ArrayList<>(prunedNodes);
                this.highlightPoints = highlightPoints == null ? Collections.emptyList() : new ArrayList<>(highlightPoints);
                this.nearestSource = nearestSource;
                this.nearestRadius = nearestRadius;
                this.currentCandidate = currentCandidate;
                repaint();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CANVAS_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(message, 10, 20);
            if (root == null) {
                g2.drawString("Tree is empty", 10, 40);
                return;
            }

            double padding = 30;
            double minX = root.minX - 10;
            double maxX = root.maxX + 10;
            double minY = root.minY - 10;
            double maxY = root.maxY + 10;
            double worldWidth = Math.max(1, maxX - minX);
            double worldHeight = Math.max(1, maxY - minY);
            double scaleX = (getWidth() - 2 * padding) / worldWidth;
            double scaleY = (getHeight() - 2 * padding) / worldHeight;

            if (queryRect != null) {
                drawRectangle(g2, queryRect[0], queryRect[1], queryRect[2], queryRect[3], minX, minY, maxX, maxY,
                        padding, scaleX, scaleY, new BasicStroke(2f), new Color(255, 87, 34));
            }

            drawNode(g2, root, minX, minY, maxX, maxY, padding, scaleX, scaleY);

            if (nearestSource != null && nearestRadius != null) {
                drawNearestGuide(g2, minX, minY, maxX, maxY, padding, scaleX, scaleY);
            }
        }

        private void drawNode(Graphics2D g2, Node node, double minX, double minY, double maxX, double maxY,
                double padding, double scaleX, double scaleY) {
            Color borderColor = node.leaf ? LEAF_COLOR : NODE_COLOR;
            if (prunedNodes.contains(node)) {
                borderColor = PRUNED_COLOR;
            } else if (visitedNodes.contains(node)) {
                borderColor = VISITED_COLOR;
            }
            BasicStroke stroke = node.leaf ? new BasicStroke(2f) : new BasicStroke(1.5f);
            drawRectangle(g2, node.minX, node.minY, node.maxX, node.maxY, minX, minY, maxX, maxY, padding, scaleX,
                    scaleY, stroke, borderColor);

            if (node.leaf) {
                for (Point point : node.points) {
                    drawPoint(g2, point, minX, minY, maxX, maxY, padding, scaleX, scaleY,
                            highlightPoints.contains(point));
                }
            } else {
                for (Node child : node.children) {
                    drawNode(g2, child, minX, minY, maxX, maxY, padding, scaleX, scaleY);
                }
            }
        }

        private void drawRectangle(Graphics2D g2, double rectMinX, double rectMinY, double rectMaxX, double rectMaxY,
                double minX, double minY, double maxX, double maxY, double padding, double scaleX, double scaleY,
                BasicStroke stroke, Color color) {
            int x1 = (int) Math.round(padding + (rectMinX - minX) * scaleX);
            int x2 = (int) Math.round(padding + (rectMaxX - minX) * scaleX);
            int y1 = (int) Math.round(padding + (maxY - rectMaxY) * scaleY);
            int y2 = (int) Math.round(padding + (maxY - rectMinY) * scaleY);
            int width = Math.max(2, x2 - x1);
            int height = Math.max(2, y2 - y1);
            g2.setColor(color);
            g2.setStroke(stroke);
            g2.drawRect(x1, y1, width, height);
        }

        private void drawPoint(Graphics2D g2, Point point, double minX, double minY, double maxX, double maxY,
                double padding, double scaleX, double scaleY, boolean highlight) {
            double screenX = padding + (point.getX() - minX) * scaleX;
            double screenY = padding + (maxY - point.getY()) * scaleY;
            double size = highlight ? 10 : 6;
            double offset = size / 2.0;
            g2.setColor(highlight ? HIGHLIGHT_POINT_COLOR : POINT_COLOR);
            g2.fill(new Ellipse2D.Double(screenX - offset, screenY - offset, size, size));
            if (highlight && currentCandidate != null && point == currentCandidate) {
                g2.setColor(Color.BLACK);
                g2.drawString("★", (float) screenX + 4, (float) screenY - 4);
            }
        }

        private void drawNearestGuide(Graphics2D g2, double minX, double minY, double maxX, double maxY, double padding,
                double scaleX, double scaleY) {
            double sx = padding + (nearestSource.getX() - minX) * scaleX;
            double sy = padding + (maxY - nearestSource.getY()) * scaleY;
            g2.setColor(new Color(121, 85, 72));
            g2.fill(new Ellipse2D.Double(sx - 5, sy - 5, 10, 10));
            double rectMinX = nearestSource.getX() - nearestRadius;
            double rectMaxX = nearestSource.getX() + nearestRadius;
            double rectMinY = nearestSource.getY() - nearestRadius;
            double rectMaxY = nearestSource.getY() + nearestRadius;
            drawRectangle(g2, rectMinX, rectMinY, rectMaxX, rectMaxY, minX, minY, maxX, maxY, padding, scaleX, scaleY,
                    new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 6f, 6f }, 0),
                    new Color(121, 85, 72));
        }
    }
}
