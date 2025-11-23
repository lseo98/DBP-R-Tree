package org.dfpl.dbp.rtree;

import java.util.*;

public class PerformanceTest {

    // ==========================
    // 선형 탐색 함수들
    // ==========================

    // Range Search (Linear)
    static List<Point> linearSearch(List<Point> allPoints, Rectangle rect) {
        List<Point> result = new ArrayList<>();
        for (Point p : allPoints) {
            if (p.getX() >= rect.getLeftTop().getX() &&
                    p.getX() <= rect.getRightBottom().getX() &&
                    p.getY() >= rect.getLeftTop().getY() &&
                    p.getY() <= rect.getRightBottom().getY()) {
                result.add(p);
            }
        }
        return result;
    }

    // KNN (Linear)
    static List<Point> linearKNN(List<Point> allPoints, Point source, int k) {
        List<Point> copy = new ArrayList<>(allPoints);
        copy.sort(Comparator.comparingDouble(p ->
                Math.sqrt(Math.pow(p.getX() - source.getX(), 2)
                        + Math.pow(p.getY() - source.getY(), 2))));
        return copy.subList(0, Math.min(k, copy.size()));
    }

    // ==========================
    // main
    // ==========================

    public static void main(String[] args) {

        System.out.println("=== R-Tree vs Linear Search 성능 비교 ===\n");

        int[] dataSizes = {50};
        int[] queryCount = {20};

        for (int idx = 0; idx < dataSizes.length; idx++) {

            int dataSize = dataSizes[idx];
            int queries = queryCount[idx];

            System.out.println("데이터셋: " + dataSize + " 포인트");
            System.out.println("쿼리 수: " + queries);
            System.out.println("----------------------------------");

            Random rand = new Random(42);

            // ==========================
            // 랜덤 데이터 생성
            // ==========================
            List<Point> allPoints = new ArrayList<>();
            for (int i = 0; i < dataSize; i++) {
                allPoints.add(new Point(rand.nextDouble() * 200, rand.nextDouble() * 200));
            }

            // ==========================
            // RTree 생성 및 삽입
            // ==========================
            RTree rtree = new RTreeImpl();
            for (Point p : allPoints) {
                rtree.add(p);
            }

            // ==========================
            // 범위 검색 (Range)
            // ==========================
            System.out.println("[범위 검색]");

            long linearRange = 0;
            long rtreeRange = 0;

            for (int q = 0; q < queries; q++) {

                double x = rand.nextDouble() * 150;
                double y = rand.nextDouble() * 150;
                Rectangle rect = new Rectangle(
                        new Point(x, y),
                        new Point(x + 30, y + 30)
                );

                // Linear
                long s1 = System.nanoTime();
                linearSearch(allPoints, rect);
                long e1 = System.nanoTime();
                linearRange += (e1 - s1);

                // R-tree
                long s2 = System.nanoTime();
                Iterator<Point> it = rtree.search(rect);
                while (it.hasNext()) it.next();
                long e2 = System.nanoTime();
                rtreeRange += (e2 - s2);
            }

            System.out.printf("선형 탐색: %.2f µs/쿼리\n", linearRange / (double) queries / 1000);
            System.out.printf("R-Tree 탐색: %.2f µs/쿼리\n", rtreeRange / (double) queries / 1000);

            // ==========================
            // KNN
            // ==========================
            System.out.println("\n[KNN 검색]");

            long linearKnn = 0;
            long rtreeKnn = 0;

            for (int q = 0; q < queries; q++) {

                Point src = new Point(rand.nextDouble() * 200, rand.nextDouble() * 200);

                // Linear
                long s1 = System.nanoTime();
                linearKNN(allPoints, src, 10);
                long e1 = System.nanoTime();
                linearKnn += (e1 - s1);

                // R-tree
                long s2 = System.nanoTime();
                Iterator<Point> it = rtree.nearest(src, 10);
                while (it.hasNext()) it.next();
                long e2 = System.nanoTime();
                rtreeKnn += (e2 - s2);
            }

            System.out.printf("선형 KNN : %.2f µs/쿼리\n", linearKnn / (double) queries / 1000);
            System.out.printf("R-Tree KNN : %.2f µs/쿼리\n", rtreeKnn / (double) queries / 1000);

            System.out.println("\n======================================\n");
        }
    }
}
