package org.dfpl.dbp.rtree.team1;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.PriorityQueue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

// RTreeImpl 클래스. RTree 인터페이스를 구현하고 GUI 및 모든 로직을 포함.
public class RTreeImpl implements RTree {

    private static final int MAX_ENTRIES = 4;
    private static final int MIN_ENTRIES = 2;

    private Node root;
    private int size;
    private boolean enableGUI; // GUI 활성화 여부

    private JFrame guiFrame;
    private RTreePanel guiPanel;
    private volatile Rectangle currentSearchRect;
    private volatile Point currentKnnSource;
    private volatile List<Point> highlightedPoints = Collections.synchronizedList(new ArrayList<>());
    private boolean firstDeleteCall = true;

    // 시각화 강조용 필드
    private volatile Point lastInsertedPoint;
    private volatile Rectangle lastExpandedMbr;
    private volatile Node splitGroup1;
    private volatile Node splitGroup2;
    private volatile List<Node> prunedNodes = Collections.synchronizedList(new ArrayList<>());

    // KNN 시각화
    private volatile List<Node> knnVisitedNodes = Collections.synchronizedList(new ArrayList<>());
    private volatile java.util.Map<Node, Integer> knnVisitOrder = Collections
            .synchronizedMap(new java.util.HashMap<>());
    private volatile Node knnCurrentNode; // 현재 방문 중인 노드
    private volatile double knnCurrentBestDist = Double.POSITIVE_INFINITY;
    private volatile List<Point> knnCandidatePoints = Collections.synchronizedList(new ArrayList<>()); // 후보 점들
    private volatile java.util.Map<Point, Double> knnResultDistances = Collections
            .synchronizedMap(new java.util.HashMap<>()); // 최종 결과 점들의 거리
    private volatile Point knnNewlyFoundPoint; // 새로 발견된 점 (선 그리기용)

    // Delete 시각화
    private volatile Point deletingPoint;
    private volatile Node deletingLeafNode; // 삭제 대상이 속한 리프
    private volatile Rectangle oldMbrBeforeDelete;
    private volatile Rectangle shrinkingMbr;
    private volatile List<Node> underflowNodes = Collections.synchronizedList(new ArrayList<>()); // 언더플로우 노드

    // Node 내부 클래스. 트리의 노드(가지 또는 나뭇잎)를 정의.
    class Node {
        Rectangle mbr;
        boolean leaf;
        List<Node> children;
        List<Point> points;
        Node parent;

        public Node(boolean leaf, Node parent) {
            this.leaf = leaf;
            this.parent = parent;
            this.mbr = createInvalidMbr();
            if (leaf) {
                this.points = new ArrayList<>(MAX_ENTRIES + 1);
                this.children = null;
            } else {
                this.children = new ArrayList<>(MAX_ENTRIES + 1);
                this.points = null;
            }
        }

        public Rectangle getMbr() {
            if (!hasValidMbr()) {
                return createInvalidMbr();
            }
            return this.mbr;
        }

        public Rectangle getEntryMbr(Object entry) {
            if (entry instanceof Point) {
                Point p = (Point) entry;
                return new Rectangle(p, p);
            } else if (entry instanceof Node) {
                return ((Node) entry).getMbr();
            }
            return createInvalidMbr();
        }

        public boolean hasValidMbr() {
            return mbr.getLeftTop().getX() != Double.POSITIVE_INFINITY;
        }

        public void recalcMbr() {
            if (leaf) {
                if (points.isEmpty()) {
                    this.mbr = createInvalidMbr();
                    return;
                }
                Point p = points.get(0);
                double minX = p.getX(), minY = p.getY(), maxX = p.getX(), maxY = p.getY();
                for (int i = 1; i < points.size(); i++) {
                    p = points.get(i);
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                }
                this.mbr = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
            } else {
                if (children.isEmpty()) {
                    this.mbr = createInvalidMbr();
                    return;
                }

                Node c = children.stream().filter(Node::hasValidMbr).findFirst().orElse(null);
                if (c == null) {
                    this.mbr = createInvalidMbr();
                    return;
                }

                double minX = c.mbr.getLeftTop().getX();
                double minY = c.mbr.getLeftTop().getY();
                double maxX = c.mbr.getRightBottom().getX();
                double maxY = c.mbr.getRightBottom().getY();

                for (int i = 0; i < children.size(); i++) {
                    c = children.get(i);
                    if (!c.hasValidMbr())
                        continue;
                    minX = Math.min(minX, c.mbr.getLeftTop().getX());
                    minY = Math.min(minY, c.mbr.getLeftTop().getY());
                    maxX = Math.max(maxX, c.mbr.getRightBottom().getX());
                    maxY = Math.max(maxY, c.mbr.getRightBottom().getY());
                }
                this.mbr = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
            }
        }
    }

    // RTreePanel 내부 클래스. Swing의 JPanel을 상속받아 R-Tree를 화면에 그림.
    class RTreePanel extends JPanel {
        private final int PADDING = 60;
        // 계층별 MBR 색상
        private final Color COLOR_ROOT = new Color(148, 0, 211); // 보라 (Purple)
        private final Color COLOR_INTERNAL = new Color(0, 0, 255); // 파랑 (Blue)
        private final Color COLOR_LEAF = new Color(0, 128, 0); // 초록 (Green)
        private double dataMaxX = 200;
        private double dataMaxY = 200;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int panelWidth = getWidth();
            int panelHeight = getHeight();
            double scaleX = (panelWidth - 2 * PADDING) / dataMaxX;
            double scaleY = (panelHeight - 2 * PADDING) / dataMaxY;
            double scale = Math.min(scaleX, scaleY);
            int yOffset = panelHeight - PADDING;

            // ========== 레이어 1: 배경 (격자 + 축) ==========
            drawGrid(g2d, scale, yOffset, panelWidth, panelHeight);

            // ========== 레이어 2: 기본 MBR (계층별 색상) ==========
            if (root != null) {
                drawDepthMBR(g2d, root, 0, scale, yOffset);
            }

            // ========== 레이어 3: 효과 레이어 ==========

            // 3-1. Split 그룹 강조
            if (splitGroup1 != null && splitGroup1.hasValidMbr()) {
                drawSplitOverlay(g2d, splitGroup1, new Color(0, 180, 255, 70), scale, yOffset, "Group 1");
            }
            if (splitGroup2 != null && splitGroup2.hasValidMbr()) {
                drawSplitOverlay(g2d, splitGroup2, new Color(255, 120, 200, 70), scale, yOffset, "Group 2");
            }

            // 3-2. Range Search 영역 + Pruned 표시
            if (currentSearchRect != null) {
                drawPrunedNodes(g2d, scale, yOffset);
                drawSearchArea(g2d, scale, yOffset);
            }

            // 3-3. KNN 방문 노드 + 거리 원 + Pruned 표시
            drawKNNVisualization(g2d, scale, yOffset);

            // 3-4. 삭제 대상 리프 강조
            if (deletingLeafNode != null) {
                drawDeletingLeaf(g2d, scale, yOffset);
            }

            // 3-5. 삭제 전 MBR (shrink 애니메이션)
            if (oldMbrBeforeDelete != null && shrinkingMbr != null) {
                drawShrinkAnimation(g2d, scale, yOffset);
            }

            // 3-6. 확장된 MBR 강조 (Split 없을 때만)
            if (lastExpandedMbr != null && splitGroup1 == null && splitGroup2 == null) {
                drawExpandedMBR(g2d, scale, yOffset);
            }

            // ========== 레이어 4: 모든 점 ==========
            if (root != null) {
                drawAllPoints(g2d, root, scale, yOffset);
            }

            // ========== 레이어 5: 하이라이트 점 ==========
            drawHighlightedPoints(g2d, scale, yOffset);

            // ========== 레이어 6: 애니메이션 요소 ==========

            // 6-1. 새로 삽입된 점
            if (lastInsertedPoint != null) {
                drawNewPoint(g2d, scale, yOffset);
            }

            // 6-2. 삭제 중인 점
            if (deletingPoint != null) {
                drawDeletingPoint(g2d, scale, yOffset);
            }
        }

        // 격자 그리기
        private void drawGrid(Graphics2D g, double scale, int yOffset, int panelWidth, int panelHeight) {
            g.setColor(new Color(220, 220, 220));
            g.setStroke(new java.awt.BasicStroke(1));

            // 세로선 + X축 눈금
            for (int i = 0; i <= 200; i += 20) {
                int x = (int) (i * scale) + PADDING;
                g.drawLine(x, PADDING, x, yOffset);
                g.setColor(Color.DARK_GRAY);
                g.drawString(String.valueOf(i), x - 8, yOffset + 15);
                g.setColor(new Color(220, 220, 220));
            }

            // 가로선 + Y축 눈금
            for (int i = 0; i <= 200; i += 20) {
                int y = yOffset - (int) (i * scale);
                g.drawLine(PADDING, y, panelWidth - PADDING, y);
                g.setColor(Color.DARK_GRAY);
                g.drawString(String.valueOf(i), PADDING - 25, y + 4);
                g.setColor(new Color(220, 220, 220));
            }

            // 축 그리기
            g.setColor(Color.BLACK);
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawLine(PADDING, yOffset, panelWidth - PADDING, yOffset); // X축
            g.drawLine(PADDING, PADDING, PADDING, yOffset); // Y축
            g.setStroke(new java.awt.BasicStroke(1));
        }

        // ========== 레이어별 그리기 메서드 ==========

        // 계층별 MBR 그리기 (Root=보라, Internal=파랑, Leaf=초록, 모두 투명하게)
        private void drawDepthMBR(Graphics2D g, Node node, int depth, double scale, int yOffset) {
            if (node == null || !node.hasValidMbr())
                return;

            // 좌표 계산
            Rectangle mbr = node.getMbr();
            int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
            int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
            int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
            int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

            if (node == root) {
                // Root: 아주 투명한 보라색
                g.setColor(new Color(148, 0, 211, 20));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(148, 0, 211, 100));
                g.setStroke(new java.awt.BasicStroke(1.5f));
                g.drawRect(x, y, w, h);
                g.setStroke(new java.awt.BasicStroke(1));
            } else if (node.leaf) {
                // Leaf: 아주 투명한 초록색
                g.setColor(new Color(0, 180, 0, 25));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(0, 150, 0, 120));
                g.setStroke(new java.awt.BasicStroke(1.0f));
                g.drawRect(x, y, w, h);
                g.setStroke(new java.awt.BasicStroke(1));
            } else {
                // Internal: 아주 투명한 파란색
                g.setColor(new Color(0, 120, 255, 20));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(0, 100, 255, 100));
                g.setStroke(new java.awt.BasicStroke(1.0f));
                g.drawRect(x, y, w, h);
                g.setStroke(new java.awt.BasicStroke(1));
            }

            // 자식 노드 재귀 호출
            if (!node.leaf && node.children != null) {
                for (Node child : node.children) {
                    drawDepthMBR(g, child, depth + 1, scale, yOffset);
                }
            }
        }

        // Split 오버레이
        private void drawSplitOverlay(Graphics2D g, Node node, Color color, double scale, int yOffset, String label) {
            Rectangle mbr = node.getMbr();
            int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
            int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
            int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
            int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

            g.setColor(color);
            g.fillRect(x, y, w, h);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawRect(x, y, w, h);
            g.setStroke(new java.awt.BasicStroke(1));

            g.setColor(Color.BLACK);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
            g.drawString(label, x + w / 2 - 20, y + h / 2);
        }

        // Range Search 영역
        private void drawSearchArea(Graphics2D g, double scale, int yOffset) {
            int x = (int) (currentSearchRect.getLeftTop().getX() * scale) + PADDING;
            int y = yOffset - (int) (currentSearchRect.getRightBottom().getY() * scale);
            int w = (int) ((currentSearchRect.getRightBottom().getX() - currentSearchRect.getLeftTop().getX()) * scale);
            int h = (int) ((currentSearchRect.getRightBottom().getY() - currentSearchRect.getLeftTop().getY()) * scale);

            g.setColor(new Color(100, 150, 255, 50));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0, 100, 255));
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawRect(x, y, w, h);
            g.setStroke(new java.awt.BasicStroke(1));
        }

        // Range Search의 Pruned 노드 표시 + 점들 표시
        private void drawPrunedNodes(Graphics2D g, double scale, int yOffset) {
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
            for (Node pruned : new ArrayList<>(prunedNodes)) {
                if (!pruned.hasValidMbr())
                    continue;

                Rectangle mbr = pruned.getMbr();
                int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
                int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
                int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
                int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

                // 회색 빗금 패턴
                g.setColor(new Color(150, 150, 150, 80));
                g.fillRect(x, y, w, h);

                // 빗금
                g.setColor(new Color(100, 100, 100, 150));
                g.setStroke(new java.awt.BasicStroke(1));
                for (int i = 0; i < w + h; i += 10) {
                    int x1 = x + Math.min(i, w);
                    int y1 = y + Math.max(0, i - w);
                    int x2 = x + Math.max(0, i - h);
                    int y2 = y + Math.min(i, h);
                    g.drawLine(x1, y1, x2, y2);
                }

                // X 표시
                g.setColor(new Color(200, 0, 0));
                g.setStroke(new java.awt.BasicStroke(3));
                g.drawLine(x + 5, y + 5, x + w - 5, y + h - 5);
                g.drawLine(x + w - 5, y + 5, x + 5, y + h - 5);
                g.setStroke(new java.awt.BasicStroke(1));

                // "PRUNED" 텍스트
                g.setColor(Color.RED);
                g.drawString("PRUNED", x + w / 2 - 25, y + h / 2);

                // Pruned 노드 안의 점들도 회색으로 표시
                drawPrunedPoints(g, pruned, scale, yOffset);
            }
        }

        // Delete 시각화: 삭제 대상 리프 강조
        private void drawDeletingLeaf(Graphics2D g, double scale, int yOffset) {
            if (deletingLeafNode == null || !deletingLeafNode.hasValidMbr())
                return;

            Rectangle mbr = deletingLeafNode.getMbr();
            int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
            int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
            int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
            int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

            // 노란 테두리 강조
            g.setColor(new Color(255, 200, 0, 100));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(255, 200, 0));
            g.setStroke(new java.awt.BasicStroke(3));
            g.drawRect(x, y, w, h);
            g.setStroke(new java.awt.BasicStroke(1));
        }

        // KNN 시각화 (수정본: MBR 가림 현상 해결 + 탐색 범위 명확화)
        private void drawKNNVisualization(Graphics2D g, double scale, int yOffset) {
            if (currentKnnSource == null)
                return;

            int qx = (int) (currentKnnSource.getX() * scale) + PADDING;
            int qy = yOffset - (int) (currentKnnSource.getY() * scale);

            // 1. 탐색 반경 원 (Search Wavefront)
            if (knnCurrentBestDist < Double.POSITIVE_INFINITY && knnCurrentBestDist > 0) {
                int radius = (int) (knnCurrentBestDist * scale);

                // 내부를 아주 옅게 칠해서 뒤를 가리지 않음
                g.setColor(new Color(0, 100, 255, 10));
                g.fillOval(qx - radius, qy - radius, 2 * radius, 2 * radius);

                // 테두리는 선명하게
                g.setColor(new Color(0, 50, 200));
                g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_BUTT,
                        java.awt.BasicStroke.JOIN_MITER, 10, new float[] { 10, 5 }, 0));
                g.drawOval(qx - radius, qy - radius, 2 * radius, 2 * radius);
                g.setStroke(new java.awt.BasicStroke(1));

                // 거리 텍스트
                g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 11));
                g.drawString(String.format("Dist: %.1f", knnCurrentBestDist), qx + radius + 5, qy);
            }

            // 2. Pruned nodes (가지치기 된 노드) + 해당 점들 표시
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
            for (Node pruned : new ArrayList<>(prunedNodes)) {
                if (!pruned.hasValidMbr())
                    continue;
                Rectangle mbr = pruned.getMbr();
                int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
                int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
                int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
                int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

                // 회색 투명도 조절
                g.setColor(new Color(100, 100, 100, 50));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(200, 0, 0));
                g.drawLine(x, y, x + w, y + h);
                g.drawLine(x + w, y, x, y + h);
                g.drawString("PRUNED", x + w / 2 - 20, y + h / 2);

                // Pruned 노드 안의 점들도 회색으로 표시
                drawPrunedPoints(g, pruned, scale, yOffset);
            }

            // 3. 방문한 노드들 (테두리만 그려서 뒤 MBR 보이게)
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
            for (Node visited : new ArrayList<>(knnVisitedNodes)) {
                if (!visited.hasValidMbr() || visited == knnCurrentNode)
                    continue;

                Rectangle mbr = visited.getMbr();
                int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
                int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
                int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
                int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

                // 채우기 없음! 테두리만
                g.setColor(new Color(255, 140, 0, 180));
                g.setStroke(new java.awt.BasicStroke(2));
                g.drawRect(x, y, w, h);
                g.setStroke(new java.awt.BasicStroke(1));

                // 방문 순서 번호
                Integer order = knnVisitOrder.get(visited);
                if (order != null) {
                    g.setColor(new Color(255, 69, 0)); // 진한 오렌지
                    g.drawString("#" + order, x + 5, y + 15);
                }
            }

            // 4. 현재 방문 중인 노드 (강조)
            if (knnCurrentNode != null && knnCurrentNode.hasValidMbr()) {
                Rectangle mbr = knnCurrentNode.getMbr();
                int x = (int) (mbr.getLeftTop().getX() * scale) + PADDING;
                int y = yOffset - (int) (mbr.getRightBottom().getY() * scale);
                int w = (int) ((mbr.getRightBottom().getX() - mbr.getLeftTop().getX()) * scale);
                int h = (int) ((mbr.getRightBottom().getY() - mbr.getLeftTop().getY()) * scale);

                // 현재 노드는 아주 옅게 채워서 강조
                g.setColor(new Color(255, 100, 0, 40));
                g.fillRect(x, y, w, h);

                // 굵은 테두리
                g.setColor(new Color(255, 0, 0));
                g.setStroke(new java.awt.BasicStroke(3));
                g.drawRect(x, y, w, h);
                g.setStroke(new java.awt.BasicStroke(1));

                g.setColor(Color.RED);
                g.drawString("VISITING", x + 5, y - 5);
            }

            // 5. 후보 점들 (초록색)
            for (Point candidate : new ArrayList<>(knnCandidatePoints)) {
                int px = (int) (candidate.getX() * scale) + PADDING;
                int py = yOffset - (int) (candidate.getY() * scale);
                g.setColor(new Color(0, 180, 0));
                g.fillOval(px - 4, py - 4, 8, 8);
            }

            // 6. 새로 발견된 점 연결선
            if (knnNewlyFoundPoint != null) {
                int px = (int) (knnNewlyFoundPoint.getX() * scale) + PADDING;
                int py = yOffset - (int) (knnNewlyFoundPoint.getY() * scale);

                g.setColor(Color.RED);
                g.setStroke(new java.awt.BasicStroke(2));
                g.drawLine(qx, qy, px, py);
                g.setStroke(new java.awt.BasicStroke(1));

                // 거리 표시
                double dist = currentKnnSource.distance(knnNewlyFoundPoint);
                g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 11));
                g.setColor(Color.RED);
                g.drawString(String.format("%.2f", dist), (qx + px) / 2 + 5, (qy + py) / 2 - 5);
            }

            // 7. Query Point (중심점)
            g.setColor(Color.RED);
            g.fillOval(qx - 6, qy - 6, 12, 12);
            g.setColor(Color.WHITE);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
            g.drawString("S", qx - 3, qy + 4);
        }

        // Pruned 노드 안의 점들을 회색으로 표시
        private void drawPrunedPoints(Graphics2D g, Node node, double scale, int yOffset) {
            if (node.leaf && node.points != null) {
                // 리프 노드면 점들 표시
                for (Point p : node.points) {
                    int px = (int) (p.getX() * scale) + PADDING;
                    int py = yOffset - (int) (p.getY() * scale);

                    // 회색 X 표시
                    g.setColor(new Color(120, 120, 120));
                    g.fillOval(px - 4, py - 4, 8, 8);
                    g.setColor(new Color(80, 80, 80));
                    g.setStroke(new java.awt.BasicStroke(2));
                    g.drawLine(px - 3, py - 3, px + 3, py + 3);
                    g.drawLine(px - 3, py + 3, px + 3, py - 3);
                    g.setStroke(new java.awt.BasicStroke(1));
                }
            } else if (!node.leaf && node.children != null) {
                // 내부 노드면 자식들 재귀 탐색
                for (Node child : node.children) {
                    drawPrunedPoints(g, child, scale, yOffset);
                }
            }
        }

        // Shrink 애니메이션
        private void drawShrinkAnimation(Graphics2D g, double scale, int yOffset) {
            // 이전 MBR (점선)
            int x1 = (int) (oldMbrBeforeDelete.getLeftTop().getX() * scale) + PADDING;
            int y1 = yOffset - (int) (oldMbrBeforeDelete.getRightBottom().getY() * scale);
            int w1 = (int) ((oldMbrBeforeDelete.getRightBottom().getX() - oldMbrBeforeDelete.getLeftTop().getX())
                    * scale);
            int h1 = (int) ((oldMbrBeforeDelete.getRightBottom().getY() - oldMbrBeforeDelete.getLeftTop().getY())
                    * scale);
            g.setColor(new Color(255, 100, 100, 150));
            g.setStroke(new java.awt.BasicStroke(2, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10,
                    new float[] { 5 }, 0));
            g.drawRect(x1, y1, w1, h1);

            // 새 MBR (실선)
            int x2 = (int) (shrinkingMbr.getLeftTop().getX() * scale) + PADDING;
            int y2 = yOffset - (int) (shrinkingMbr.getRightBottom().getY() * scale);
            int w2 = (int) ((shrinkingMbr.getRightBottom().getX() - shrinkingMbr.getLeftTop().getX()) * scale);
            int h2 = (int) ((shrinkingMbr.getRightBottom().getY() - shrinkingMbr.getLeftTop().getY()) * scale);
            g.setColor(new Color(0, 200, 0));
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawRect(x2, y2, w2, h2);
            g.setStroke(new java.awt.BasicStroke(1));
        }

        // 확장된 MBR 강조
        private void drawExpandedMBR(Graphics2D g, double scale, int yOffset) {
            int x = (int) (lastExpandedMbr.getLeftTop().getX() * scale) + PADDING;
            int y = yOffset - (int) (lastExpandedMbr.getRightBottom().getY() * scale);
            int w = (int) ((lastExpandedMbr.getRightBottom().getX() - lastExpandedMbr.getLeftTop().getX()) * scale);
            int h = (int) ((lastExpandedMbr.getRightBottom().getY() - lastExpandedMbr.getLeftTop().getY()) * scale);

            g.setColor(new Color(255, 200, 0));
            g.setStroke(new java.awt.BasicStroke(3));
            g.drawRect(x, y, w, h);
            g.setStroke(new java.awt.BasicStroke(1));
        }

        // 모든 점 그리기
        private void drawAllPoints(Graphics2D g, Node node, double scale, int yOffset) {
            if (node == null)
                return;

            if (node.leaf && node.points != null) {
                for (Point p : node.points) {
                    int px = (int) (p.getX() * scale) + PADDING;
                    int py = yOffset - (int) (p.getY() * scale);
                    g.setColor(Color.BLACK);
                    g.fillOval(px - 2, py - 2, 4, 4);
                }
            } else if (node.children != null) {
                for (Node child : node.children) {
                    drawAllPoints(g, child, scale, yOffset);
                }
            }
        }

        // 하이라이트 점들
        private void drawHighlightedPoints(Graphics2D g, double scale, int yOffset) {
            List<Point> points = new ArrayList<>(highlightedPoints);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 11));

            // Range Search: 순서 없이 강조만
            if (currentSearchRect != null) {
                for (Point p : points) {
                    int x = (int) (p.getX() * scale) + PADDING;
                    int y = yOffset - (int) (p.getY() * scale);
                    g.setColor(new Color(255, 0, 0));
                    g.fillOval(x - 5, y - 5, 10, 10);
                    g.setColor(Color.BLACK);
                    g.setStroke(new java.awt.BasicStroke(2));
                    g.drawOval(x - 5, y - 5, 10, 10);
                    g.setStroke(new java.awt.BasicStroke(1));
                }
            }
            // KNN: 순서 + 거리 표시
            else if (currentKnnSource != null) {
                int idx = 1;
                for (Point p : points) {
                    int x = (int) (p.getX() * scale) + PADDING;
                    int y = yOffset - (int) (p.getY() * scale);

                    // 점 크게 표시
                    g.setColor(new Color(0, 100, 255));
                    g.fillOval(x - 7, y - 7, 14, 14);
                    g.setColor(Color.WHITE);
                    g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
                    String label = String.valueOf(idx++);
                    int labelWidth = g.getFontMetrics().stringWidth(label);
                    g.drawString(label, x - labelWidth / 2, y + 4);

                    // 거리 표시
                    Double dist = knnResultDistances.get(p);
                    if (dist != null) {
                        g.setColor(Color.BLACK);
                        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
                        g.drawString(String.format("%.2f", dist), x + 10, y + 15);
                    }
                }
            }
        }

        // 새로 삽입된 점
        private void drawNewPoint(Graphics2D g, double scale, int yOffset) {
            int x = (int) (lastInsertedPoint.getX() * scale) + PADDING;
            int y = yOffset - (int) (lastInsertedPoint.getY() * scale);

            // 바깥 펄스
            g.setColor(new Color(0, 255, 0, 100));
            g.fillOval(x - 12, y - 12, 24, 24);
            // 안쪽 점
            g.setColor(new Color(0, 255, 0));
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setColor(Color.BLACK);
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawOval(x - 5, y - 5, 10, 10);
            g.setStroke(new java.awt.BasicStroke(1));
        }

        // 삭제 중인 점
        private void drawDeletingPoint(Graphics2D g, double scale, int yOffset) {
            int x = (int) (deletingPoint.getX() * scale) + PADDING;
            int y = yOffset - (int) (deletingPoint.getY() * scale);

            g.setColor(new Color(255, 0, 0));
            g.setStroke(new java.awt.BasicStroke(3));
            g.drawLine(x - 7, y - 7, x + 7, y + 7);
            g.drawLine(x + 7, y - 7, x - 7, y + 7);
            g.setStroke(new java.awt.BasicStroke(1));
        }
    }

    // DistSpat 내부 클래스. KNN 검색 시 우선순위 큐(PQ)에 거리와 객체를 함께 저장.
    private class DistSpat implements Comparable<DistSpat> {
        final double dist;
        final Object item;

        DistSpat(Object item, double dist) {
            this.item = item;
            this.dist = dist;
        }

        @Override
        public int compareTo(DistSpat other) {
            return Double.compare(this.dist, other.dist);
        }
    }

    // RTreeImpl 생성자. Assignment45가 new RTreeImpl()을 호출할 때 GUI 창을 생성.
    public RTreeImpl() {
        this(true);
    }

    // RTreeImpl 생성자 (GUI 제어용). enableGUI가 false면 시각화 없이 동작.
    public RTreeImpl(boolean enableGUI) {
        this.root = new Node(true, null);
        this.size = 0;
        this.enableGUI = enableGUI;

        if (enableGUI) {
            SwingUtilities.invokeLater(() -> {
                this.guiFrame = new JFrame("R-Tree 시각화 (Assignment 45) - Quadratic Split");
                this.guiPanel = new RTreePanel();
                this.guiFrame.add(this.guiPanel);
                this.guiFrame.setSize(800, 800);
                this.guiFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                this.guiFrame.setVisible(true);
            });
        }
    }

    // updateGUI 헬퍼 메서드. GUI를 갱신할 때 스레드 문제를 해결(EDT에서 실행).
    private void updateGUI() {
        if (this.guiPanel != null) {
            SwingUtilities.invokeLater(() -> {
                guiPanel.repaint();
            });
        }
    }

    // 벤치마크용 메서드: GUI 없이 빠르게 추가 (중복 체크 제거로 성능 향상)
    public void addFast(Point point) {
        Node leaf = chooseLeaf(root, point);
        insert(leaf, point);
        this.size++;
    }

    // 벤치마크용 메서드: GUI 없이 빠르게 범위 검색
    public Iterator<Point> searchFast(Rectangle rectangle) {
        List<Point> result = new ArrayList<>();
        searchRecursive(root, rectangle, result);
        return result.iterator();
    }

    // 벤치마크용 메서드: GUI 없이 빠르게 k-NN 검색 (제곱 거리 최적화)
    public Iterator<Point> nearestFast(Point source, int maxCount) {
        PriorityQueue<DistSpat> pq = new PriorityQueue<>();
        pq.add(new DistSpat(root, 0.0));
        List<Point> result = new ArrayList<>();

        // 소스 좌표 캐싱
        double sx = source.getX();
        double sy = source.getY();

        while (!pq.isEmpty() && result.size() < maxCount) {
            DistSpat current = pq.poll();

            if (current.item instanceof Point) {
                Point p = (Point) current.item;
                result.add(p);
            } else {
                Node node = (Node) current.item;
                if (node.leaf) {
                    for (Point p : node.points) {
                        // 제곱 거리 사용 (sqrt 제거)
                        double dx = p.getX() - sx;
                        double dy = p.getY() - sy;
                        double distSq = dx * dx + dy * dy;
                        pq.add(new DistSpat(p, distSq));
                    }
                } else {
                    for (Node child : node.children) {
                        if (child.hasValidMbr()) {
                            double minDistSq = rectMinDistanceSquared(child.getMbr(), source);
                            pq.add(new DistSpat(child, minDistSq));
                        }
                    }
                }
            }
        }
        return result.iterator();
    }

    // rectMinDistance의 제곱 거리 버전 (벤치마크 최적화용)
    private double rectMinDistanceSquared(Rectangle r, Point p) {
        if (r.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        double px = p.getX();
        double py = p.getY();
        double minX = r.getLeftTop().getX();
        double minY = r.getLeftTop().getY();
        double maxX = r.getRightBottom().getX();
        double maxY = r.getRightBottom().getY();

        double dx = 0;
        double dy = 0;
        if (px < minX) {
            dx = minX - px;
        } else if (px > maxX) {
            dx = px - maxX;
        }
        if (py < minY) {
            dy = minY - py;
        } else if (py > maxY) {
            dy = py - maxY;
        }
        return dx * dx + dy * dy; // sqrt 제거
    }

    // add 메서드. 포인트를 삽입하고, Thread.sleep으로 애니메이션 효과를 줌.
    @Override
    public void add(Point point) {
        this.currentSearchRect = null;
        this.currentKnnSource = null;
        this.highlightedPoints.clear();
        this.prunedNodes.clear();
        this.knnVisitedNodes.clear();
        this.splitGroup1 = null;
        this.splitGroup2 = null;
        this.deletingPoint = null;
        this.shrinkingMbr = null;

        if (findLeaf(root, point) != null) {
            return;
        }

        Node leaf = chooseLeaf(root, point);

        // 확장되는 MBR 저장 (삽입 전)
        if (leaf.hasValidMbr()) {
            this.lastExpandedMbr = new Rectangle(leaf.mbr.getLeftTop(), leaf.mbr.getRightBottom());
        }

        // 새로 삽입되는 점 강조
        this.lastInsertedPoint = point;

        insert(leaf, point);
        this.size++;
        updateGUI();

        if (enableGUI) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 강조 해제
        this.lastInsertedPoint = null;
        this.lastExpandedMbr = null;
        this.splitGroup1 = null;
        this.splitGroup2 = null;
        updateGUI();
    }

    // search 메서드. 범위 검색을 수행하고, Task 1 완료를 JOptionPane으로 알림.
    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        // Task 1 완료 모달 먼저 (현재 트리 결과를 보여주면서)
        JOptionPane.showMessageDialog(guiFrame,
                "Task 1 complete\n\nPress OK to start Range Search...",
                "Task 1 Complete", JOptionPane.INFORMATION_MESSAGE);

        // 확인 누르면 초기화 + 검색 시작
        this.currentSearchRect = null;
        this.currentKnnSource = null;
        this.highlightedPoints.clear();
        this.prunedNodes.clear();
        this.knnVisitedNodes.clear();
        this.knnCandidatePoints.clear();
        this.lastInsertedPoint = null;
        this.lastExpandedMbr = null;
        this.splitGroup1 = null;
        this.splitGroup2 = null;

        List<Point> result = new ArrayList<>();
        this.currentSearchRect = rectangle;
        this.highlightedPoints.clear();
        this.prunedNodes.clear();

        // 1. 검색 영역 표시
        updateGUI();
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
        }

        // 2. Pruned 노드를 수집하면서 검색 (애니메이션)
        searchRecursiveWithPruning(root, rectangle, result);

        // 3. 최종 결과 표시
        this.highlightedPoints.addAll(result);
        updateGUI();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        // Task 2 완료 모달 (Range Search 결과를 보여주면서)
        JOptionPane.showMessageDialog(guiFrame,
                "Task 2 complete\n\nFound " + result.size() + " points\n\nPress OK to continue...",
                "Task 2 Complete", JOptionPane.INFORMATION_MESSAGE);

        return result.iterator();
    }

    // Pruning 시각화를 포함한 검색
    private void searchRecursiveWithPruning(Node node, Rectangle rectangle, List<Point> result) {
        if (node.leaf) {
            // Leaf 노드 도달 → 점들 하나씩 추가
            for (Point p : node.points) {
                if (rectContains(rectangle, p)) {
                    result.add(p);
                    this.highlightedPoints.clear();
                    this.highlightedPoints.addAll(result);
                    updateGUI();
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException e) {
                    }
                }
            }
        } else {
            for (Node child : node.children) {
                if (rectIntersects(child.getMbr(), rectangle)) {
                    // 검색 범위와 겹침 → 재귀 탐색
                    searchRecursiveWithPruning(child, rectangle, result);
                } else {
                    // Pruned 노드 기록 + 시각화
                    prunedNodes.add(child);
                    collectAllDescendants(child, prunedNodes);
                    updateGUI();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    // 모든 자손 노드 수집 (pruning 표시용)
    private void collectAllDescendants(Node node, List<Node> list) {
        if (!node.leaf && node.children != null) {
            for (Node child : node.children) {
                list.add(child);
                collectAllDescendants(child, list);
            }
        }
    }

    // nearest 메서드. KNN 검색을 수행하고, Task 2 완료를 JOptionPane으로 알림.
    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        // Task 2 완료 모달 먼저 (Range Search 결과를 보여주면서)
        JOptionPane.showMessageDialog(guiFrame,
                "Task 2 complete\n\nPress OK to start KNN search...",
                "Task 2 Complete", JOptionPane.INFORMATION_MESSAGE);

        // 확인 누르면 초기화 + KNN 검색 시작
        this.currentSearchRect = null;
        this.prunedNodes.clear();
        this.knnVisitedNodes.clear();
        this.knnVisitOrder.clear();
        this.knnCandidatePoints.clear();
        this.knnResultDistances.clear();
        this.knnNewlyFoundPoint = null;
        this.knnCurrentNode = null;
        this.highlightedPoints.clear();
        this.lastInsertedPoint = null;
        this.lastExpandedMbr = null;
        this.splitGroup1 = null;
        this.splitGroup2 = null;
        this.knnCurrentBestDist = Double.POSITIVE_INFINITY;
        this.currentKnnSource = source;

        PriorityQueue<DistSpat> pq = new PriorityQueue<>();
        pq.add(new DistSpat(root, 0.0));
        List<Point> result = new ArrayList<>();
        int visitOrder = 1;

        while (!pq.isEmpty() && result.size() < maxCount) {
            DistSpat current = pq.poll();

            if (current.item instanceof Point) {
                // Point pop: 결과에 추가
                Point p = (Point) current.item;
                result.add(p);
                double dist = source.distance(p);

                // 새로 발견된 점 시각화 (Source에서 선 그리기)
                this.knnNewlyFoundPoint = p;
                this.knnCandidatePoints.remove(p);
                updateGUI();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }

                // bestDist 업데이트 + 원 그리기
                this.knnCurrentBestDist = dist;
                this.knnResultDistances.put(p, dist);
                this.knnNewlyFoundPoint = null;

                // 결과 표시
                this.highlightedPoints.clear();
                this.highlightedPoints.addAll(result);
                updateGUI();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                }

            } else {
                // Node pop: 현재 방문 노드 강조
                Node node = (Node) current.item;
                this.knnCurrentNode = node;
                knnVisitedNodes.add(node);
                knnVisitOrder.put(node, visitOrder++);
                updateGUI();
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                }

                if (node.leaf) {
                    // Leaf 도달 → 점들을 후보로 추가
                    for (Point p : node.points) {
                        this.knnCandidatePoints.add(p);
                        pq.add(new DistSpat(p, rectMinDistance(new Rectangle(p, p), source)));
                    }
                    updateGUI();
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                    }
                } else {
                    // 내부 노드 → 자식들 PQ에 추가 + Pruning 체크
                    for (Node child : node.children) {
                        if (child.hasValidMbr()) {
                            double minDist = rectMinDistance(child.getMbr(), source);
                            // Pruning: bestDist보다 멀면 탐색 안 함
                            if (result.size() == maxCount && minDist > knnCurrentBestDist) {
                                prunedNodes.add(child);
                                collectAllDescendants(child, prunedNodes);
                                updateGUI();
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                }
                            } else {
                                pq.add(new DistSpat(child, minDist));
                            }
                        }
                    }
                }

                // 현재 노드 처리 완료
                this.knnCurrentNode = null;
            }
        }

        // 최종 결과 표시 (모든 보조 요소 제거, 결과만 남김)
        this.knnVisitedNodes.clear();
        this.knnVisitOrder.clear();
        this.knnCandidatePoints.clear();
        this.prunedNodes.clear();
        this.highlightedPoints.clear();
        this.highlightedPoints.addAll(result);
        // knnCurrentBestDist와 knnResultDistances는 유지 (원과 거리 표시)
        updateGUI();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        // Task 3 완료 모달 (KNN 결과를 보여주면서)
        JOptionPane.showMessageDialog(guiFrame,
                "Task 3 complete\n\nFound " + result.size() + " nearest points\n\nPress OK to continue...",
                "Task 3 Complete", JOptionPane.INFORMATION_MESSAGE);

        return result.iterator();
    }

    // delete 메서드. 포인트를 삭제하고, 첫 호출 시 Task 3 완료를 알림.
    @Override
    public void delete(Point point) {
        if (firstDeleteCall) {
            firstDeleteCall = false;

            // Task 3 완료 모달 먼저 (KNN 결과를 보여주면서)
            JOptionPane.showMessageDialog(guiFrame,
                    "Task 3 complete\n\nPress OK to start deletion...",
                    "Task 3 Complete", JOptionPane.INFORMATION_MESSAGE);

            // 확인 누르면 초기화
            this.currentSearchRect = null;
            this.currentKnnSource = null;
            this.highlightedPoints.clear();
            this.prunedNodes.clear();
            this.knnVisitedNodes.clear();
            this.knnVisitOrder.clear();
            this.knnCandidatePoints.clear();
            this.lastInsertedPoint = null;
            this.lastExpandedMbr = null;
            this.splitGroup1 = null;
            this.splitGroup2 = null;
            updateGUI();
        }

        Node leaf = findLeaf(root, point);
        if (leaf == null) {
            return;
        }
        Point toRemove = null;
        for (Point p : leaf.points) {
            if (p.getX() == point.getX() && p.getY() == point.getY()) {
                toRemove = p;
                break;
            }
        }
        if (toRemove == null) {
            return;
        }

        // 1. 삭제 대상 점 + 리프 강조
        this.deletingPoint = point;
        this.deletingLeafNode = leaf;
        updateGUI();
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
        }

        // 2. 삭제 전 MBR 저장
        this.oldMbrBeforeDelete = new Rectangle(leaf.mbr.getLeftTop(), leaf.mbr.getRightBottom());

        // 3. 점 제거
        leaf.points.remove(toRemove);
        leaf.recalcMbr();

        // 4. Shrink 애니메이션 (old MBR → new MBR)
        if (leaf.hasValidMbr()) {
            this.shrinkingMbr = new Rectangle(leaf.mbr.getLeftTop(), leaf.mbr.getRightBottom());
        }
        this.deletingPoint = null; // 점은 사라졌으니 X 표시 제거
        updateGUI();
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
        }

        // 5. Tree 재조정
        condenseTree(leaf);
        this.size--;

        if (size == 0) {
            root = new Node(true, null);
        }

        // 6. 강조 해제
        this.deletingLeafNode = null;
        this.shrinkingMbr = null;
        this.oldMbrBeforeDelete = null;
        updateGUI();
    }

    // isEmpty 메서드. 모든 Task 완료 후 최종 결과를 JOptionPane으로 알림.
    @Override
    public boolean isEmpty() {
        updateGUI();
        boolean result = (this.size == 0);

        JOptionPane.showMessageDialog(guiFrame,
                "Task 4 complete",
                "Task 4 complete", JOptionPane.INFORMATION_MESSAGE);

        return result;
    }

    // chooseLeaf 메서드. 면적 증가(enlargement)가 가장 적은 하위 노드를 선택.
    private Node chooseLeaf(Node node, Point point) {
        if (node.leaf) {
            return node;
        }
        Node bestChild = null;
        double minEnlargement = Double.POSITIVE_INFINITY;

        for (Node child : node.children) {
            double enlargement = rectEnlargement(child.getMbr(), point);

            if (enlargement < minEnlargement) {
                minEnlargement = enlargement;
                bestChild = child;
            } else if (enlargement == minEnlargement) {
                if (bestChild == null || rectArea(child.getMbr()) < rectArea(bestChild.getMbr())) {
                    bestChild = child;
                }
            }
        }
        if (bestChild == null) {
            bestChild = node.children.get(0);
        }
        return chooseLeaf(bestChild, point);
    }

    // insert 메서드. 리프에 삽입 후, 오버플로우 시 splitNode를 호출.
    private void insert(Node leaf, Point point) {
        leaf.points.add(point);
        leaf.recalcMbr();
        if (leaf.points.size() > MAX_ENTRIES) {
            Node[] newNodes = splitNode(leaf);
            adjustTree(newNodes[0], newNodes[1]);
        } else {
            adjustTree(leaf, null);
        }
    }

    private Node[] splitNode(Node node) {
        List entries;
        if (node.leaf) {
            entries = new ArrayList<>(node.points);
        } else {
            entries = new ArrayList<>(node.children);
        }
        Node group1 = node;
        Node group2 = new Node(node.leaf, node.parent);
        Object[] seeds = pickSeeds(entries, node);
        Object seed1 = seeds[0];
        Object seed2 = seeds[1];

        if (node.leaf) {
            group1.points.clear();
            group1.points.add((Point) seed1);
            group2.points.add((Point) seed2);
        } else {
            group1.children.clear();
            group1.children.add((Node) seed1);
            ((Node) seed1).parent = group1;
            group2.children.add((Node) seed2);
            ((Node) seed2).parent = group2;
        }

        // IMPORTANT: make sure MBRs reflect the seed assignments before pickNext
        group1.recalcMbr();
        group2.recalcMbr();

        entries.remove(seed1);
        entries.remove(seed2);

        while (!entries.isEmpty()) {
            // Ensure minimum entries (m = MIN_ENTRIES) constraint
            if (group1.leaf) {
                if (group1.points.size() + entries.size() == MIN_ENTRIES) {
                    // give all remaining entries to group1
                    for (Object e : new ArrayList<>(entries)) {
                        group1.points.add((Point) e);
                    }
                    entries.clear();
                    break;
                }
                if (group2.points.size() + entries.size() == MIN_ENTRIES) {
                    for (Object e : new ArrayList<>(entries)) {
                        group2.points.add((Point) e);
                    }
                    entries.clear();
                    break;
                }
            } else {
                if (group1.children.size() + entries.size() == MIN_ENTRIES) {
                    for (Object e : new ArrayList<>(entries)) {
                        group1.children.add((Node) e);
                        ((Node) e).parent = group1;
                    }
                    entries.clear();
                    break;
                }
                if (group2.children.size() + entries.size() == MIN_ENTRIES) {
                    for (Object e : new ArrayList<>(entries)) {
                        group2.children.add((Node) e);
                        ((Node) e).parent = group2;
                    }
                    entries.clear();
                    break;
                }
            }

            Object nextEntry = pickNext(entries, group1, group2, node);
            Rectangle mbr1 = group1.getMbr();
            Rectangle mbr2 = group2.getMbr();
            Rectangle entryMbr = node.getEntryMbr(nextEntry);

            double cost1 = rectEnlargement(mbr1, entryMbr);
            double cost2 = rectEnlargement(mbr2, entryMbr);

            if (cost1 < cost2) {
                if (node.leaf)
                    group1.points.add((Point) nextEntry);
                else {
                    group1.children.add((Node) nextEntry);
                    ((Node) nextEntry).parent = group1;
                }
            } else if (cost2 < cost1) {
                if (node.leaf)
                    group2.points.add((Point) nextEntry);
                else {
                    group2.children.add((Node) nextEntry);
                    ((Node) nextEntry).parent = group2;
                }
            } else {
                if (rectArea(mbr1) < rectArea(mbr2)) {
                    if (node.leaf)
                        group1.points.add((Point) nextEntry);
                    else {
                        group1.children.add((Node) nextEntry);
                        ((Node) nextEntry).parent = group1;
                    }
                } else if (rectArea(mbr2) < rectArea(mbr1)) {
                    if (node.leaf)
                        group2.points.add((Point) nextEntry);
                    else {
                        group2.children.add((Node) nextEntry);
                        ((Node) nextEntry).parent = group2;
                    }
                } else {
                    int size1 = node.leaf ? group1.points.size() : group1.children.size();
                    int size2 = node.leaf ? group2.points.size() : group2.children.size();
                    if (size1 <= size2) {
                        if (node.leaf)
                            group1.points.add((Point) nextEntry);
                        else {
                            group1.children.add((Node) nextEntry);
                            ((Node) nextEntry).parent = group1;
                        }
                    } else {
                        if (node.leaf)
                            group2.points.add((Point) nextEntry);
                        else {
                            group2.children.add((Node) nextEntry);
                            ((Node) nextEntry).parent = group2;
                        }
                    }
                }
            }

            // keep MBRs up-to-date so the next iteration's cost calculations are accurate
            group1.recalcMbr();
            group2.recalcMbr();
            entries.remove(nextEntry);
        }
        group1.recalcMbr();
        group2.recalcMbr();

        // Split visualization (GUI가 켜져 있을 때만 실행)
        if (enableGUI) {
            this.splitGroup1 = group1;
            this.splitGroup2 = group2;
            updateGUI();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

        return new Node[] { group1, group2 };
    }

    // pickSeeds 헬퍼 메서드. Quadratic Split의 첫 단계로, 가장 낭비되는 공간이 큰 두 시드를 고름.
    private Object[] pickSeeds(List entries, Node node) {
        Object seed1 = null;
        Object seed2 = null;
        double maxWastedArea = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Object e1 = entries.get(i);
                Object e2 = entries.get(j);
                Rectangle mbr1 = node.getEntryMbr(e1);
                Rectangle mbr2 = node.getEntryMbr(e2);

                Rectangle mergedMbr = rectMerge(mbr1, mbr2);
                double wastedArea = rectArea(mergedMbr) - rectArea(mbr1) - rectArea(mbr2);

                if (wastedArea > maxWastedArea) {
                    maxWastedArea = wastedArea;
                    seed1 = e1;
                    seed2 = e2;
                }
            }
        }
        return new Object[] { seed1, seed2 };
    }

    // pickNext 헬퍼 메서드. Quadratic Split의 두 번째 단계로, 그룹 선호도 차이가 가장 큰 엔트리를 고름.
    private Object pickNext(List entries, Node group1, Node group2, Node node) {
        Object nextEntry = null;
        double maxDiff = Double.NEGATIVE_INFINITY;
        Rectangle mbr1 = group1.getMbr();
        Rectangle mbr2 = group2.getMbr();

        for (Object entry : entries) {
            Rectangle entryMbr = node.getEntryMbr(entry);
            double cost1 = rectEnlargement(mbr1, entryMbr);
            double cost2 = rectEnlargement(mbr2, entryMbr);
            double diff = Math.abs(cost1 - cost2);

            if (diff > maxDiff) {
                maxDiff = diff;
                nextEntry = entry;
            }
        }
        return nextEntry;
    }

    // adjustTree 메서드. 삽입/분할 후 트리를 재조정. (루트 MBR 버그 수정된 버전)
    private void adjustTree(Node node, Node newNode) {
        Node n = node;
        Node nn = newNode;
        while (n != root) {
            Node parent = n.parent;
            if (parent == null)
                break;

            // 핵심 로직. 자식(nn)을 먼저 추가하고, MBR을 재계산해야 .
            if (nn != null) {
                parent.children.add(nn);
                nn.parent = parent;
            }
            parent.recalcMbr();

            if (parent.children.size() > MAX_ENTRIES) {
                Node[] splitParents = splitNode(parent);
                n = splitParents[0];
                nn = splitParents[1];
            } else {
                n = parent;
                nn = null;
            }
        }

        // 루트 분할 처리 로직.
        if (nn != null) {
            Node newRoot = new Node(false, null);
            newRoot.children.add(n);
            newRoot.children.add(nn);
            n.parent = newRoot;
            nn.parent = newRoot;
            newRoot.recalcMbr();
            this.root = newRoot;
        } else {
            // nn == null인 경우에도 root MBR 재계산 (중요!)
            root.recalcMbr();
        }
    }

    // searchRecursive 헬퍼 메서드. MBR이 겹치는지(intersects) 확인하여 가지치기(Pruning)를 수행.
    private void searchRecursive(Node node, Rectangle rectangle, List<Point> result) {
        if (node.leaf) {
            for (Point p : node.points) {
                if (rectContains(rectangle, p)) {
                    result.add(p);
                }
            }
        } else {
            for (Node child : node.children) {
                if (rectIntersects(child.getMbr(), rectangle)) {
                    searchRecursive(child, rectangle, result);
                }
            }
        }
    }

    // findLeaf 헬퍼 메서드. 삭제할 포인트가 어느 리프에 있는지 찾음.
    private Node findLeaf(Node node, Point point) {
        if (node.leaf) {
            for (Point p : node.points) {
                if (p.getX() == point.getX() && p.getY() == point.getY()) {
                    return node;
                }
            }
            return null;
        }
        for (Node child : node.children) {
            if (rectContains(child.getMbr(), point)) {
                Node result = findLeaf(child, point);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    // condenseTree 메서드. 삭제 후 트리를 압축(언더플로우 처리, MBR 축소).
    private void condenseTree(Node node) {
        Node n = node;
        while (n != root) {
            Node parent = n.parent;
            if (n.leaf && n.points.size() < MIN_ENTRIES) {
                handleUnderflow(n);
            } else if (!n.leaf && n.children.size() < MIN_ENTRIES) {
                handleUnderflow(n);
            } else {
                n.recalcMbr();
            }
            if (parent == null)
                break;

            // handleUnderflow에서 n이 제거됐는지 확인 후 부모로 이동.
            if (parent.children.contains(n)) {
                n = parent;
            } else {
                n = parent;
            }
        }

        // 루트 높이 축소 로직.
        if (!root.leaf && root.children.size() == 1) {
            Node oldRoot = root;
            root = root.children.get(0);
            root.parent = null;
            oldRoot.children.clear();
        }
    }

    // handleUnderflow 헬퍼 메서드. m=2개 미만인 노드를 형제와 병합(Merge) 또는 재분배(Redistribution).
    private void handleUnderflow(Node node) {
        Node parent = node.parent;
        if (parent == null)
            return;
        Node sibling = null;
        for (Node s : parent.children) {
            if (s != node) {
                sibling = s;
                break;
            }
        }
        if (sibling == null) {
            return;
        }

        if (node.leaf) {
            // 1. 합병 가능한 경우 (두 노드를 합쳐도 MAX_ENTRIES 이하)
            if (sibling.points.size() + node.points.size() <= MAX_ENTRIES) {
                sibling.points.addAll(node.points);
                parent.children.remove(node);
                node.points.clear();
                sibling.recalcMbr();
            }
            // 2. 합병 불가능한 경우 -> 재분배(Redistribution)
            else {
                // 형제한테서 빌려와서 MIN_ENTRIES 개수 맞추기
                while (node.points.size() < MIN_ENTRIES && sibling.points.size() > MIN_ENTRIES) {
                    Point borrow = sibling.points.remove(sibling.points.size() - 1);
                    node.points.add(0, borrow);
                }
                node.recalcMbr();
                sibling.recalcMbr();
            }
        } else {
            // 내부 노드 처리
            // 1. 합병 가능한 경우
            if (sibling.children.size() + node.children.size() <= MAX_ENTRIES) {
                sibling.children.addAll(node.children);
                for (Node child : node.children) {
                    child.parent = sibling;
                }
                parent.children.remove(node);
                node.children.clear();
                sibling.recalcMbr();
            }
            // 2. 합병 불가능한 경우 -> 재분배(Redistribution)
            else {
                // 형제한테서 빌려와서 MIN_ENTRIES 개수 맞추기
                while (node.children.size() < MIN_ENTRIES && sibling.children.size() > MIN_ENTRIES) {
                    Node borrow = sibling.children.remove(sibling.children.size() - 1);
                    borrow.parent = node; // 부모 변경 중요!
                    node.children.add(0, borrow);
                }
                node.recalcMbr();
                sibling.recalcMbr();
            }
        }
    }

    // -----------------------------------------------------------------
    // 5. Rectangle 헬퍼 메서드
    // -----------------------------------------------------------------

    // createInvalidMbr 헬퍼 메서드. 비어있는 MBR을 정의.
    private Rectangle createInvalidMbr() {
        return new Rectangle(
                new Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                new Point(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
    }

    // rectArea 헬퍼 메서드. 사각형의 면적을 계산.
    private double rectArea(Rectangle r) {
        if (r.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return 0;
        }
        double width = r.getRightBottom().getX() - r.getLeftTop().getX();
        double height = r.getRightBottom().getY() - r.getLeftTop().getY();
        return width * height;
    }

    // rectContains 헬퍼 메서드. 사각형이 점을 포하는지 확인.
    private boolean rectContains(Rectangle r, Point p) {
        if (r == null || p == null || r.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return false;
        }
        return p.getX() >= r.getLeftTop().getX() &&
                p.getX() <= r.getRightBottom().getX() &&
                p.getY() >= r.getLeftTop().getY() &&
                p.getY() <= r.getRightBottom().getY();
    }

    // rectIntersects 헬퍼 메서드. 두 사각형이 겹치는지 확인.
    private boolean rectIntersects(Rectangle r1, Rectangle r2) {
        if (r1 == null || r2 == null)
            return false;
        if (r1.getLeftTop().getX() == Double.POSITIVE_INFINITY ||
                r2.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return false;
        }
        return !(r2.getRightBottom().getX() < r1.getLeftTop().getX() ||
                r2.getLeftTop().getX() > r1.getRightBottom().getX() ||
                r2.getRightBottom().getY() < r1.getLeftTop().getY() ||
                r2.getLeftTop().getY() > r1.getRightBottom().getY());
    }

    // rectEnlargement 헬퍼 메서드. 점을 포할 때 면적 증가량을 계산.
    private double rectEnlargement(Rectangle r, Point p) {
        if (p == null)
            return 0;
        if (r.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return 0;
        }
        double newMinX = Math.min(r.getLeftTop().getX(), p.getX());
        double newMinY = Math.min(r.getLeftTop().getY(), p.getY());
        double newMaxX = Math.max(r.getRightBottom().getX(), p.getX());
        double newMaxY = Math.max(r.getRightBottom().getY(), p.getY());
        double newArea = (newMaxX - newMinX) * (newMaxY - newMinY);
        return newArea - rectArea(r);
    }

    // rectEnlargement 헬퍼 메서드 오버로딩. 사각형을 포할 때 면적 증가량을 계산.
    private double rectEnlargement(Rectangle r1, Rectangle r2) {
        if (r2 == null || r2.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return 0;
        }
        if (r1.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return rectArea(r2);
        }
        double newMinX = Math.min(r1.getLeftTop().getX(), r2.getLeftTop().getX());
        double newMinY = Math.min(r1.getLeftTop().getY(), r2.getLeftTop().getY());
        double newMaxX = Math.max(r1.getRightBottom().getX(), r2.getRightBottom().getX());
        double newMaxY = Math.max(r1.getRightBottom().getY(), r2.getRightBottom().getY());
        double newArea = (newMaxX - newMinX) * (newMaxY - newMinY);
        return newArea - rectArea(r1);
    }

    // rectMerge 헬퍼 메서드. 두 사각형을 병합(merge)하여 새 MBR을 반환.
    private Rectangle rectMerge(Rectangle r1, Rectangle r2) {
        if (r1.getLeftTop().getX() == Double.POSITIVE_INFINITY &&
                r2.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return createInvalidMbr();
        }
        if (r1.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return new Rectangle(r2.getLeftTop(), r2.getRightBottom());
        }
        if (r2.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return new Rectangle(r1.getLeftTop(), r1.getRightBottom());
        }
        double newMinX = Math.min(r1.getLeftTop().getX(), r2.getLeftTop().getX());
        double newMinY = Math.min(r1.getLeftTop().getY(), r2.getLeftTop().getY());
        double newMaxX = Math.max(r1.getRightBottom().getX(), r2.getRightBottom().getX());
        double newMaxY = Math.max(r1.getRightBottom().getY(), r2.getRightBottom().getY());
        return new Rectangle(new Point(newMinX, newMinY), new Point(newMaxX, newMaxY));
    }

    // rectMinDistance 헬퍼 메서드. 사각형과 점 사이의 최소 거리(MINDIST)를 계산.
    private double rectMinDistance(Rectangle r, Point p) {
        if (r.getLeftTop().getX() == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = 0;
        double dy = 0;
        if (p.getX() < r.getLeftTop().getX()) {
            dx = r.getLeftTop().getX() - p.getX();
        } else if (p.getX() > r.getRightBottom().getX()) {
            dx = p.getX() - r.getRightBottom().getX();
        }
        if (p.getY() < r.getLeftTop().getY()) {
            dy = r.getLeftTop().getY() - p.getY();
        } else if (p.getY() > r.getRightBottom().getY()) {
            dy = p.getY() - r.getRightBottom().getY();
        }
        if (dx == 0 && dy == 0)
            return 0.0;
        return Math.sqrt(dx * dx + dy * dy);
    }
}