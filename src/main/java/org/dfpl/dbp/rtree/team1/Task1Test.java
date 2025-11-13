package org.dfpl.dbp.rtree.team1;

import java.util.List;

/**
 * Task 1 테스트: 삽입 + GUI 시각화
 */
public class Task1Test {

	public static void main(String[] args) {
		// 30개 Point 리스트
		List<Point> pointList = List.of(new Point(20, 30), new Point(25, 25), new Point(30, 40), new Point(35, 20),
				new Point(40, 35), new Point(15, 45), new Point(45, 15), new Point(28, 32), new Point(30, 150),
				new Point(40, 170), new Point(50, 140), new Point(25, 160), new Point(55, 175), new Point(60, 155),
				new Point(45, 135), new Point(38, 145), new Point(160, 60), new Point(170, 70), new Point(155, 80),
				new Point(180, 55), new Point(175, 90), new Point(165, 95), new Point(150, 75), new Point(185, 85),
				new Point(70, 80), new Point(95, 90), new Point(120, 100), new Point(80, 110), new Point(130, 40),
				new Point(100, 65));

		// R-Tree 생성
		RTreeImpl rTree = new RTreeImpl();

		// GUI 시각화 활성화
		rTree.enableVisualization();

		System.out.println("=== Task 1: R-Tree 삽입 + GUI 시각화 시작 ===\n");

		// Point 순차 삽입
		for (Point point : pointList) {
			rTree.add(point);
		}

		System.out.println("\n=== Task 1 완료 ===");
		System.out.println("총 포인트 수: " + rTree.getSize());
		System.out.println("트리 높이: " + rTree.getHeight());
		System.out.println("\nGUI 창을 확인하세요. 계층적 MBR과 모든 Point가 표시됩니다.");
	}
}
