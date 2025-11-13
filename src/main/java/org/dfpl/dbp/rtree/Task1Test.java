package org.dfpl.dbp.rtree;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Scanner;

/**
 * 통합 테스트: Task 1 (삽입) + Task 2 (탐색)
 *
 * 실행 흐름:
 * 1. Task 1: 30개 Point를 순차적으로 삽입 (GUI 시각화)
 * 2. GUI 창에서 드롭다운으로 Task 2로 전환
 * 3. Task 2: 범위 탐색 테스트 (검색 범위, 방문 노드, 가지치기 노드 시각화)
 */
public class Task1Test {

    private static RTreeImpl rTree;
    private static RTreeVisualizer visualizer;

    public static void main(String[] args) {
        // 30개 Point 리스트
        List<Point> pointList = List.of(
                new Point(20, 30), new Point(25, 25), new Point(30, 40), new Point(35, 20),
                new Point(40, 35), new Point(15, 45), new Point(45, 15), new Point(28, 32),
                new Point(30, 150), new Point(40, 170), new Point(50, 140), new Point(25, 160),
                new Point(55, 175), new Point(60, 155), new Point(45, 135), new Point(38, 145),
                new Point(160, 60), new Point(170, 70), new Point(155, 80), new Point(180, 55),
                new Point(175, 90), new Point(165, 95), new Point(150, 75), new Point(185, 85),
                new Point(70, 80), new Point(95, 90), new Point(120, 100), new Point(80, 110),
                new Point(130, 40), new Point(100, 65)
        );

        // R-Tree 생성
        rTree = new RTreeImpl();

        // GUI 시각화 활성화
        rTree.enableVisualization();
        visualizer = rTree.getVisualizer();

        System.out.println("=== Task 1: R-Tree 삽입 + GUI 시각화 시작 ===\n");

        // ===== TASK 1: Point 순차 삽입 =====
        System.out.println("▶ Task 1: 30개 Point 삽입 중...\n");
        for (Point point : pointList) {
            rTree.add(point);
        }

        System.out.println("\n✓ Task 1 완료!");
        System.out.println("총 포인트 수: " + rTree.getSize());
        System.out.println("트리 높이: " + rTree.getHeight());
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("GUI 창 우측 상단의 드롭다운에서 'Task 2: Search'로 전환하세요.");
        System.out.println("또는 터미널에 엔터를 눌러 자동 Task 2 시연을 시작합니다.");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        // 사용자 입력 대기
        Scanner scanner = new Scanner(System.in);
        System.out.print("Task 2 탐색을 시작하려면 엔터를 누르세요 >>> ");
        scanner.nextLine();

        // ===== TASK 2: 범위 탐색 테스트 =====
        runTask2Tests();

        System.out.println("\n=== 모든 테스트 완료 ===");
        System.out.println("GUI 창을 닫으려면 X 버튼을 클릭하세요.");

        scanner.close();
    }

    /**
     * Task 2: 범위 탐색 테스트
     *
     * 3가지 검색 범위로 시연:
     * 1. 좌측 상단 영역 (Point 많음) → 많은 노드 방문
     * 2. 우측 영역 (Point 적음) → 일부 노드만 방문
     * 3. 우상단 영역 (Point 없음) → 많은 노드 가지치기
     */
    private static void runTask2Tests() {
        System.out.println("▶ Task 2: 범위 탐색 시연\n");
        System.out.println("GUI 창 드롭다운을 'Task 2: Search'로 변경했다면,");
        System.out.println("다음 3가지 탐색 범위가 순차적으로 시각화됩니다.\n");

        try {
            // 테스트 1: 좌측 하단 영역 (많은 Point)
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[테스트 1] 범위: (15, 15) ~ (50, 50)");
            System.out.println("예상: 많은 노드 방문, 가지치기 적음");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            Rectangle searchRect1 = new Rectangle(
                    new Point(15, 15),
                    new Point(50, 50)
            );
            performSearch(searchRect1, "검색 1");
            Thread.sleep(2000);

            // 테스트 2: 중앙 우측 영역
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[테스트 2] 범위: (150, 50) ~ (190, 100)");
            System.out.println("예상: 일부 노드 방문, 많은 노드 가지치기");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            Rectangle searchRect2 = new Rectangle(
                    new Point(150, 50),
                    new Point(190, 100)
            );
            performSearch(searchRect2, "검색 2");
            Thread.sleep(2000);

            // 테스트 3: 우상단 빈 영역
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[테스트 3] 범위: (190, 180) ~ (200, 200)");
            System.out.println("예상: Point 없음, 많은 노드 가지치기");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            Rectangle searchRect3 = new Rectangle(
                    new Point(190, 180),
                    new Point(200, 200)
            );
            performSearch(searchRect3, "검색 3");
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 단일 범위 탐색 수행 및 시각화
     *
     * @param searchRect 검색 범위
     * @param testName 테스트 이름
     */
    private static void performSearch(Rectangle searchRect, String testName) {
        System.out.println("\n[실행 중] " + testName + "...");

        // ===== 트래킹: 방문한 노드 & 가지치기된 노드 =====
        Set<RTreeNode> visitedNodes = new HashSet<>();
        Set<RTreeNode> prunedNodes = new HashSet<>();

        // 탐색 수행 (트래킹 포함)
        var results = rTree.searchWithTracking(searchRect, visitedNodes, prunedNodes);

        // 통계 출력
        System.out.println("  ✓ 찾은 Point: " + results.size());
        System.out.println("  ✓ 방문한 노드: " + visitedNodes.size());
        System.out.println("  ✓ 가지치기된 노드: " + prunedNodes.size());

        // 찾은 Point 출력
        if (!results.isEmpty()) {
            System.out.print("  ✓ Point 목록: ");
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                System.out.print(results.get(i) + " ");
            }
            if (results.size() > 5) {
                System.out.print("... (" + (results.size() - 5) + "개 더)");
            }
            System.out.println();
        }

        // GUI 시각화 업데이트
        visualizer.setSearchVisualization(searchRect, visitedNodes, prunedNodes);
        visualizer.showStep("[" + testName + "] 검색 범위: " + searchRect
                + " | 결과: " + results.size()
                + " | 방문: " + visitedNodes.size()
                + " | 가지치기: " + prunedNodes.size());
    }
}