package org.dfpl.dbp.rtree.team1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.PriorityQueue;

/**
 * R-Tree 성능 비교 전용 클래스
 * 
 * 사용법:
 * 1. 표 모드: java org.dfpl.dbp.rtree.team1.PerformanceComparison table
 * 2. 데모 모드: java org.dfpl.dbp.rtree.team1.PerformanceComparison demo
 * 3. 인자 없음: 기본적으로 표 모드 실행
 * 
 * 이 클래스는 Assignment45.java와 완전히 분리되어 있으며,
 * GUI 시각화 없이 R-Tree와 선형 스캔의 성능을 비교합니다.
 */
public class PerformanceComparison {

    private static final long SEED = 42L; // 재현성을 위한 고정 시드
    private static final int WARMUP_ITERATIONS = 5; // 워밍업 횟수 (JVM 최적화)
    private static final int BENCHMARK_ITERATIONS = 20; // 벤치마크 반복 횟수 (안정적인 측정)

    /**
     * 벤치마크 결과를 저장하는 클래스
     */
    private static class BenchmarkResult {
        final int dataSize;
        final double rtreeRangeMs;
        final double linearRangeMs;
        final double rtreeKnnMs;
        final double linearKnnMs;

        BenchmarkResult(int dataSize, double rtreeRangeMs, double linearRangeMs,
                double rtreeKnnMs, double linearKnnMs) {
            this.dataSize = dataSize;
            this.rtreeRangeMs = rtreeRangeMs;
            this.linearRangeMs = linearRangeMs;
            this.rtreeKnnMs = rtreeKnnMs;
            this.linearKnnMs = linearKnnMs;
        }
    }

    /**
     * 메인 진입점
     * 
     * IDE에서 간편 실행:
     * - PerformanceTableMode.java 실행 (Ctrl+F11) → 표 모드
     * - PerformanceDemoMode.java 실행 (Ctrl+F11) → 데모 모드
     * 
     * 또는 이 클래스를 직접 실행하면 메뉴가 표시됩니다.
     */
    public static void main(String[] args) {
        String mode = null;

        if (args.length > 0) {
            mode = args[0].toLowerCase();
        } else {
            // 인자 없이 실행하면 메뉴 표시
            System.out.println("=".repeat(80));
            System.out.println("R-Tree 성능 비교 도구");
            System.out.println("=".repeat(80));
            System.out.println();
            System.out.println("실행 모드를 선택하세요:");
            System.out.println();
            System.out.println("  [1] 표 모드 (Table Mode)");
            System.out.println("      - 여러 데이터 크기 (100, 500, 1000, 5000, 10000)에 대해 비교");
            System.out.println("      - 결과를 표 형식으로 출력");
            System.out.println("      - 실행 시간: 약 30초 ~ 1분");
            System.out.println();
            System.out.println("  [2] 데모 모드 (Demo Mode)");
            System.out.println("      - 1000개 포인트로 빠른 테스트");
            System.out.println("      - 상세한 결과 및 샘플 출력");
            System.out.println("      - 실행 시간: 약 5초");
            System.out.println();
            System.out.println("=".repeat(80));
            System.out.println();
            System.out.println("팁: IDE에서 직접 실행하려면");
            System.out.println("  - PerformanceTableMode.java → Ctrl+F11");
            System.out.println("  - PerformanceDemoMode.java → Ctrl+F11");
            System.out.println();
            System.out.println("기본 모드로 표 모드를 실행합니다...");
            System.out.println();

            mode = "table";
        }

        if ("demo".equals(mode)) {
            runDemoMode();
        } else {
            runTableMode();
        }
    }

    /**
     * 표 모드: 다양한 데이터 크기에 대해 성능 비교 결과를 두 개의 표로 출력
     */
    private static void runTableMode() {
        System.out.println("=".repeat(80));
        System.out.println("R-Tree vs 선형 스캔 성능 비교 (표 모드)");
        System.out.println("=".repeat(80));
        System.out.println();

        int[] dataSizes = { 100, 500, 1000, 5000, 10000 };
        List<BenchmarkResult> results = new ArrayList<>();

        // 모든 데이터 크기에 대해 벤치마크 실행
        for (int size : dataSizes) {
            BenchmarkResult result = runBenchmark(size);
            results.add(result);
        }
        System.out.println();

        // Range 검색 표 출력
        printRangeTable(results);
        System.out.println();

        // kNN 검색 표 출력
        printKnnTable(results);

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("벤치마크 완료. 각 측정은 " + BENCHMARK_ITERATIONS + "회 반복 평균입니다.");
    }

    /**
     * Range 검색 결과 표 출력
     */
    private static void printRangeTable(List<BenchmarkResult> results) {
        System.out.println("[범위 검색 성능 비교]");
        System.out.println("-".repeat(70));
        System.out.printf("%-12s | %-15s | %-15s | %-15s%n",
                "데이터 크기", "RTree(ms)", "Linear(ms)", "Speedup");
        System.out.println("-".repeat(70));

        for (BenchmarkResult result : results) {
            double speedup = result.linearRangeMs / result.rtreeRangeMs;
            System.out.printf("%-12d | %15.3f | %15.3f | %15.2fx%n",
                    result.dataSize,
                    result.rtreeRangeMs,
                    result.linearRangeMs,
                    speedup);
        }

        System.out.println("-".repeat(70));
    }

    /**
     * kNN 검색 결과 표 출력
     */
    private static void printKnnTable(List<BenchmarkResult> results) {
        System.out.println("[kNN 검색 성능 비교]");
        System.out.println("-".repeat(70));
        System.out.printf("%-12s | %-15s | %-15s | %-15s%n",
                "데이터 크기", "RTree(ms)", "Linear(ms)", "Speedup");
        System.out.println("-".repeat(70));

        for (BenchmarkResult result : results) {
            double speedup = result.linearKnnMs / result.rtreeKnnMs;
            System.out.printf("%-12d | %15.3f | %15.3f | %15.2fx%n",
                    result.dataSize,
                    result.rtreeKnnMs,
                    result.linearKnnMs,
                    speedup);
        }

        System.out.println("-".repeat(70));
    }

    /**
     * 데모 모드: 단일 쿼리를 실행하고 결과를 상세히 출력
     */
    private static void runDemoMode() {
        System.out.println("=".repeat(80));
        System.out.println("R-Tree vs 선형 스캔 성능 비교 (데모 모드)");
        System.out.println("=".repeat(80));
        System.out.println();

        int dataSize = 1000;
        System.out.println("데이터 생성 중... (크기: " + dataSize + ")");
        List<Point> points = generateRandomPoints(dataSize, SEED);

        // R-Tree 구축 (GUI 없음)
        RTreeImpl rtree = new RTreeImpl(false);
        for (Point p : points) {
            rtree.addFast(p);
        }

        System.out.println("R-Tree 구축 완료!\n");

        // 범위 검색 테스트
        System.out.println("--- 범위 검색 테스트 ---");
        Rectangle searchRect = new Rectangle(new Point(200, 200), new Point(400, 400));
        System.out.println("검색 범위: " + searchRect);

        long startTime = System.nanoTime();
        List<Point> rtreeResults = toList(rtree.searchFast(searchRect));
        long rtreeTime = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        List<Point> linearResults = linearRangeSearch(points, searchRect);
        long linearTime = System.nanoTime() - startTime;

        System.out.printf("R-Tree 결과: %d개 점, 소요 시간: %.3f ms%n",
                rtreeResults.size(), rtreeTime / 1_000_000.0);
        System.out.printf("선형 스캔 결과: %d개 점, 소요 시간: %.3f ms%n",
                linearResults.size(), linearTime / 1_000_000.0);
        System.out.printf("속도 향상: %.2fx%n%n", (double) linearTime / rtreeTime);

        // k-NN 검색 테스트
        System.out.println("--- k-NN 검색 테스트 ---");
        Point queryPoint = new Point(500, 500);
        int k = 10;
        System.out.println("쿼리 포인트: " + queryPoint + ", k = " + k);

        startTime = System.nanoTime();
        List<Point> rtreeKnn = toList(rtree.nearestFast(queryPoint, k));
        long rtreeKnnTime = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        List<Point> linearKnn = linearKnn(points, queryPoint, k);
        long linearKnnTime = System.nanoTime() - startTime;

        System.out.printf("R-Tree 결과: %d개 점, 소요 시간: %.3f ms%n",
                rtreeKnn.size(), rtreeKnnTime / 1_000_000.0);
        System.out.printf("선형 스캔 결과: %d개 점, 소요 시간: %.3f ms%n",
                linearKnn.size(), linearKnnTime / 1_000_000.0);
        System.out.printf("속도 향상: %.2fx%n%n", (double) linearKnnTime / rtreeKnnTime);

        // 결과 샘플 출력
        System.out.println("k-NN 결과 샘플 (상위 5개):");
        for (int i = 0; i < Math.min(5, rtreeKnn.size()); i++) {
            Point p = rtreeKnn.get(i);
            double dist = queryPoint.distance(p);
            System.out.printf("  %d. %s (거리: %.2f)%n", i + 1, p, dist);
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("데모 완료!");
    }

    /**
     * 단일 데이터 크기에 대한 벤치마크 실행
     * 
     * @return BenchmarkResult 객체
     */
    private static BenchmarkResult runBenchmark(int dataSize) {
        // 데이터 생성
        List<Point> points = generateRandomPoints(dataSize, SEED);

        // R-Tree 구축 (GUI 없음)
        RTreeImpl rtree = new RTreeImpl(false);
        for (Point p : points) {
            rtree.addFast(p);
        }

        // 쿼리 생성 (중간 영역)
        Rectangle searchRect = new Rectangle(
                new Point(250, 250),
                new Point(750, 750));
        Point knnQuery = new Point(500, 500);
        int k = 10;

        // 워밍업 (JVM 최적화 유도)
        Iterator<Point> warmupIter;
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            warmupIter = rtree.searchFast(searchRect);
            while (warmupIter.hasNext())
                warmupIter.next();
            linearRangeSearch(points, searchRect);
            warmupIter = rtree.nearestFast(knnQuery, k);
            while (warmupIter.hasNext())
                warmupIter.next();
            linearKnn(points, knnQuery, k);
        }

        // GC 실행으로 측정 안정화
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }

        // 범위 검색 벤치마크 (안정적인 측정)
        long[] rtreeRangeTimes = new long[BENCHMARK_ITERATIONS];
        Iterator<Point> tempIter;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            tempIter = rtree.searchFast(searchRect);
            // Iterator 소비 (실제 검색 실행)
            while (tempIter.hasNext())
                tempIter.next();
            rtreeRangeTimes[i] = System.nanoTime() - start;
        }
        double rtreeRangeAvg = calculateMedian(rtreeRangeTimes) / 1_000_000.0;

        long[] linearRangeTimes = new long[BENCHMARK_ITERATIONS];
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            linearRangeSearch(points, searchRect);
            linearRangeTimes[i] = System.nanoTime() - start;
        }
        double linearRangeAvg = calculateMedian(linearRangeTimes) / 1_000_000.0;

        // k-NN 벤치마크 (안정적인 측정)
        long[] rtreeKnnTimes = new long[BENCHMARK_ITERATIONS];
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            tempIter = rtree.nearestFast(knnQuery, k);
            // Iterator 소비 (실제 검색 실행)
            while (tempIter.hasNext())
                tempIter.next();
            rtreeKnnTimes[i] = System.nanoTime() - start;
        }
        double rtreeKnnAvg = calculateMedian(rtreeKnnTimes) / 1_000_000.0;

        long[] linearKnnTimes = new long[BENCHMARK_ITERATIONS];
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            linearKnn(points, knnQuery, k);
            linearKnnTimes[i] = System.nanoTime() - start;
        }
        double linearKnnAvg = calculateMedian(linearKnnTimes) / 1_000_000.0;

        // 결과 반환
        return new BenchmarkResult(dataSize, rtreeRangeAvg, linearRangeAvg,
                rtreeKnnAvg, linearKnnAvg);
    }

    /**
     * 랜덤 포인트 생성 (재현 가능)
     */
    private static List<Point> generateRandomPoints(int count, long seed) {
        Random random = new Random(seed);
        List<Point> points = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double x = random.nextDouble() * 1000; // 0 ~ 1000 범위
            double y = random.nextDouble() * 1000;
            points.add(new Point(x, y));
        }

        return points;
    }

    /**
     * 선형 스캔 방식의 범위 검색
     * 모든 포인트를 순회하여 범위 내 포함 여부 확인
     * (Rectangle getter 반복 호출 최소화)
     */
    private static List<Point> linearRangeSearch(List<Point> points, Rectangle rect) {
        List<Point> result = new ArrayList<>();

        // Rectangle 좌표 캐싱 (getter 반복 호출 제거)
        double minX = rect.getLeftTop().getX();
        double minY = rect.getLeftTop().getY();
        double maxX = rect.getRightBottom().getX();
        double maxY = rect.getRightBottom().getY();

        for (Point p : points) {
            double px = p.getX();
            double py = p.getY();
            if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
                result.add(p);
            }
        }

        return result;
    }

    /**
     * 선형 스캔 방식의 k-NN 검색
     * 모든 포인트의 제곱 거리를 계산하고 정렬하여 상위 k개 반환
     * (sqrt 제거로 최적화)
     */
    private static List<Point> linearKnn(List<Point> points, Point query, int k) {
        // 우선순위 큐 사용 (최대 힙, 제곱 거리 비교)
        PriorityQueue<PointDistance> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(b.distance, a.distance));

        // 쿼리 포인트 좌표 캐싱 (getter 반복 호출 제거)
        double qx = query.getX();
        double qy = query.getY();

        for (Point p : points) {
            // sqrt 제거: 제곱 거리로 비교
            double dx = p.getX() - qx;
            double dy = p.getY() - qy;
            double distSq = dx * dx + dy * dy;

            pq.offer(new PointDistance(p, distSq));

            if (pq.size() > k) {
                pq.poll(); // 가장 먼 점 제거
            }
        }

        List<Point> result = new ArrayList<>(k);
        while (!pq.isEmpty()) {
            result.add(0, pq.poll().point); // 역순으로 추가 (가까운 순)
        }

        return result;
    }

    /**
     * Iterator를 List로 변환하는 헬퍼 메서드
     */
    private static List<Point> toList(Iterator<Point> iterator) {
        List<Point> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    /**
     * 중앙값 계산 (이상치 제거로 안정적인 측정)
     */
    private static double calculateMedian(long[] times) {
        long[] sorted = times.clone();
        java.util.Arrays.sort(sorted);
        int len = sorted.length;
        if (len % 2 == 0) {
            return (sorted[len / 2 - 1] + sorted[len / 2]) / 2.0;
        } else {
            return sorted[len / 2];
        }
    }

    /**
     * 포인트와 거리를 함께 저장하는 헬퍼 클래스
     */
    private static class PointDistance {
        final Point point;
        final double distance;

        PointDistance(Point point, double distance) {
            this.point = point;
            this.distance = distance;
        }
    }
}
