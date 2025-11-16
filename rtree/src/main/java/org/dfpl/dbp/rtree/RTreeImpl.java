package org.dfpl.dbp.rtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public void add(Point point) {
        String key = pointKey(point);
        if (pointIndex.containsKey(key)) {
            return;
        }
        pointIndex.put(key, point);
        insertInternal(point);
    }

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        if (root == null || rectangle == null) {
            return Collections.emptyIterator();
        }
        double[] bounds = normalize(rectangle);
        List<Point> result = new ArrayList<>();
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
}
