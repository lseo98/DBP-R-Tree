package org.dfpl.dbp.rtree;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RTreePanel: R-Tree를 그리는 캔버스 패널
 *
 * Swing GUI 기초:
 * - JPanel은 그림을 그릴 수 있는 컨테이너
 * - paintComponent()를 오버라이드해서 커스텀 그리기
 * - repaint()를 호출하면 다시 그려짐
 *
 * 이 클래스의 역할:
 * - R-Tree의 모든 노드와 Point를 화면에 그림
 * - 레벨별로 다른 색상으로 MBR 표시
 * - 검색/KNN 결과 시각화
 */
public class RTreePanel extends JPanel {

	// ===== 멤버 변수 =====
	private RTreeImpl tree; // 그릴 R-Tree 객체

	// 좌표 변환 상수
	// 데이터 좌표 (0~200) → 화면 좌표 (50~750)로 변환
	private static final int MARGIN = 50; // 화면 여백 (픽셀)
	private static final double SCALE = 3.5; // 확대 배율

	// 시각화 상태 변수들
	private Rectangle searchArea; // 범위 검색 영역 (Task 2용)
	private Set<RTreeNode> visitedNodes = new HashSet<>(); // 방문한 노드들
	private Set<RTreeNode> prunedNodes = new HashSet<>(); // 가지치기된 노드들
	private Point highlightPoint; // 강조 표시할 점

	/**
	 * 생성자: RTreePanel 초기화
	 *
	 * @param tree 시각화할 R-Tree
	 */
	public RTreePanel(RTreeImpl tree) {
		this.tree = tree;

		// Swing 설정
		setBackground(Color.WHITE); // 배경색 흰색
		setPreferredSize(new Dimension(800, 800)); // 패널 크기 800×800 픽셀
	}

	/**
	 * paintComponent: Swing이 화면을 그릴 때 자동으로 호출하는 메서드
	 *
	 * Swing 그리기 메커니즘:
	 * 1. repaint() 호출 → Swing에게 "다시 그려!"라고 요청
	 * 2. Swing이 적절한 타이밍에 paintComponent() 호출
	 * 3. 이 메서드 안에서 Graphics 객체로 그림 그리기
	 *
	 * @Override: 부모 클래스(JPanel)의 메서드를 재정의
	 * protected: 이 클래스와 하위 클래스에서만 접근 가능
	 *
	 * @param g Graphics 객체 (펜과 붓 같은 도구)
	 */
	@Override
	protected void paintComponent(Graphics g) {
		// ===== 1단계: 부모 클래스의 기본 그리기 =====
		// super: 부모 클래스 (JPanel)
		// super.paintComponent(g): 배경색 칠하기 등 기본 작업
		super.paintComponent(g);

		// ===== 2단계: Graphics를 Graphics2D로 형변환 =====
		// Graphics2D는 Graphics의 확장 버전
		// 더 많은 그리기 기능 제공 (선 두께, 점선 등)
		Graphics2D g2 = (Graphics2D) g;

		// ===== 3단계: 안티앨리어싱 활성화 =====
		// 안티앨리어싱: 선과 도형의 가장자리를 부드럽게 처리
		// KEY_ANTIALIASING: 안티앨리어싱 설정 키
		// VALUE_ANTIALIAS_ON: 안티앨리어싱 켜기
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// ===== 4단계: 빈 트리 처리 =====
		if (tree.getRoot() == null) {
			// 회색 텍스트로 "Empty Tree" 표시
			g2.setColor(Color.GRAY);
			// drawString(텍스트, x좌표, y좌표)
			// getWidth(): 패널의 가로 크기
			// getHeight(): 패널의 세로 크기
			g2.drawString("Empty Tree", getWidth() / 2 - 40, getHeight() / 2);
			return; // 더 이상 그릴 것 없음
		}

		// ===== 5단계: 트리 구조 그리기 =====
		drawTree(g2);

		// ===== 6단계: 통계 정보 표시 =====
		drawStatistics(g2);
	}

	/**
	 * 트리 구조 그리기
	 */
	private void drawTree(Graphics2D g2) {
		if (tree.getRoot() != null) {
			drawNodeRecursive(g2, tree.getRoot(), 0);
		}
	}

	/**
	 * drawNodeRecursive: 노드를 재귀적으로 그리기
	 *
	 * 재귀 알고리즘:
	 * 1. 현재 노드의 MBR 그리기
	 * 2. 리프 노드면 → Point들 그리기
	 * 3. 내부 노드면 → 모든 자식에 대해 재귀 호출
	 *
	 * 레벨별 시각화:
	 * - Level 0 (루트): 파란색
	 * - Level 1: 초록색
	 * - Level 2: 주황색
	 * - 리프의 Point: 빨간색
	 *
	 * @param g2 Graphics2D 객체
	 * @param node 그릴 노드
	 * @param level 현재 레벨 (0부터 시작)
	 */
	private void drawNodeRecursive(Graphics2D g2, RTreeNode node, int level) {
		// ===== 종료 조건: null 체크 =====
		if (node == null)
			return;

		// ===== MBR 색상 설정 =====
		// 레벨에 따라 다른 색상 선택
		Color color = getColorForLevel(level);
		g2.setColor(color);

		// ===== 케이스 1: 리프 노드 =====
		if (node.isLeaf()) {
			// --- 리프 MBR: 실선으로 그리기 ---
			// BasicStroke: 선 스타일 설정
			// 파라미터: (두께, 끝 스타일, 연결 스타일)
			g2.setStroke(new BasicStroke(2)); // 두께 2픽셀 실선
			drawRectangle(g2, node.getMbr(), false); // MBR 테두리만 그림

			// --- Point들 그리기 ---
			LeafNode leaf = (LeafNode) node; // 형변환
			g2.setColor(Color.RED); // 빨간색

			// 리프가 가진 모든 Point 순회
			for (Point p : leaf.getPoints()) {
				// 데이터 좌표를 화면 좌표로 변환
				Point sp = dataToScreen(p);

				// fillOval: 원 채우기
				// 파라미터: (x, y, 가로크기, 세로크기)
				// 중심이 sp가 되도록 -3 오프셋 (반지름 3)
				g2.fillOval((int) sp.getX() - 3, (int) sp.getY() - 3, 6, 6);

				// --- 강조 포인트 처리 (최근 추가된 Point) ---
				if (highlightPoint != null && p.getX() == highlightPoint.getX()
						&& p.getY() == highlightPoint.getY()) {
					// 노란색 큰 원으로 강조
					g2.setColor(Color.YELLOW);
					g2.fillOval((int) sp.getX() - 6, (int) sp.getY() - 6, 12, 12);
					g2.setColor(Color.RED); // 색상 복원
				}
			}
		}
		// ===== 케이스 2: 내부 노드 =====
		else {
			// --- 내부 MBR: 점선으로 그리기 ---
			// BasicStroke 점선 파라미터:
			// 1: 두께
			// CAP_BUTT: 선 끝 모양
			// JOIN_BEVEL: 선 연결 모양
			// 0: miter limit (연결 각도)
			// new float[]{5}: 점선 패턴 (5픽셀 선, 5픽셀 공백)
			// 0: 패턴 시작 오프셋
			g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 5 }, 0));
			drawRectangle(g2, node.getMbr(), false);

			// --- 자식 노드들 재귀 호출 ---
			InternalNode internal = (InternalNode) node;
			for (RTreeNode child : internal.getChildren()) {
				// 레벨 +1 해서 재귀 호출
				// 이렇게 트리를 따라 내려가며 모든 노드를 그림
				drawNodeRecursive(g2, child, level + 1);
			}
		}
	}

	/**
	 * 사각형 그리기
	 */
	private void drawRectangle(Graphics2D g2, Rectangle rect, boolean fill) {
		if (rect == null)
			return;

		Point lt = dataToScreen(rect.getLeftTop());
		Point rb = dataToScreen(rect.getRightBottom());

		int x = (int) lt.getX();
		int y = (int) lt.getY();
		int width = (int) (rb.getX() - lt.getX());
		int height = (int) (rb.getY() - lt.getY());

		if (fill) {
			g2.fillRect(x, y, width, height);
		} else {
			g2.drawRect(x, y, width, height);
		}
	}

	/**
	 * dataToScreen: 데이터 좌표를 화면 좌표로 변환
	 *
	 * 좌표계 설명:
	 * - 데이터 좌표: (0,0) ~ (200,200) 범위의 실제 Point 좌표
	 * - 화면 좌표: (50,50) ~ (750,750) 픽셀 위치
	 *
	 * 변환 공식:
	 * screenX = MARGIN + dataX × SCALE
	 * screenY = MARGIN + dataY × SCALE
	 *
	 * 예시:
	 * Point(0, 0) → (50, 50)
	 * Point(100, 100) → (400, 400)
	 * Point(200, 200) → (750, 750)
	 *
	 * @param p 데이터 좌표의 점
	 * @return 화면 좌표의 점
	 */
	private Point dataToScreen(Point p) {
		// 여백 추가 + 스케일 적용
		double x = MARGIN + p.getX() * SCALE;
		double y = MARGIN + p.getY() * SCALE;

		// 새로운 Point 객체 생성 (화면 좌표)
		return new Point(x, y);
	}

	/**
	 * getColorForLevel: 트리 레벨에 따른 색상 반환
	 *
	 * 색상 구분 이유:
	 * - 레벨별로 다른 색상 → 트리의 계층 구조를 시각적으로 이해하기 쉬움
	 * - 반투명 처리 (알파값 150) → 겹쳐도 아래 내용 보임
	 *
	 * @param level 트리 레벨 (0=루트)
	 * @return 해당 레벨의 색상
	 */
	private Color getColorForLevel(int level) {
		// 색상 배열 정의
		// Color 파라미터: (R, G, B, 알파)
		// 알파: 0=투명, 255=불투명, 150=반투명
		Color[] colors = { new Color(0, 0, 255, 150), // Level 0: 파랑 (루트)
				new Color(0, 255, 0, 150), // Level 1: 초록
				new Color(255, 165, 0, 150), // Level 2: 주황
				new Color(255, 0, 255, 150) // Level 3: 마젠타
		};

		// % (모듈로 연산): 레벨이 4 이상이면 순환
		// 예: level=5 → colors[1] (초록색 재사용)
		return colors[level % colors.length];
	}

	/**
	 * 통계 정보 표시
	 */
	private void drawStatistics(Graphics2D g2) {
		g2.setColor(Color.BLACK);
		g2.setFont(new Font("Monospaced", Font.PLAIN, 12));

		int y = 20;
		g2.drawString("R-Tree Statistics:", 10, y);
		y += 15;
		g2.drawString("Points: " + tree.getSize(), 10, y);
		y += 15;
		g2.drawString("Height: " + tree.getHeight(), 10, y);
	}

	// Setters for visualization state
	public void setSearchArea(Rectangle searchArea) {
		this.searchArea = searchArea;
	}

	public void setVisitedNodes(Set<RTreeNode> visitedNodes) {
		this.visitedNodes = visitedNodes;
	}

	public void setPrunedNodes(Set<RTreeNode> prunedNodes) {
		this.prunedNodes = prunedNodes;
	}

	public void setHighlightPoint(Point highlightPoint) {
		this.highlightPoint = highlightPoint;
	}
}
