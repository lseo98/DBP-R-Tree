package org.dfpl.dbp.rtree.team1;

import javax.swing.*;
import java.awt.*;

/**
 * RTreeVisualizer: R-Tree 시각화를 위한 메인 윈도우
 *
 * Swing JFrame 구조:
 * - JFrame: 최상위 윈도우 (창)
 * - BorderLayout: 5개 영역으로 나누는 레이아웃
 *   - NORTH (북): 상단
 *   - SOUTH (남): 하단
 *   - EAST (동): 오른쪽
 *   - WEST (서): 왼쪽
 *   - CENTER (중앙): 가운데 (가장 큰 공간)
 *
 * 이 윈도우의 구조:
 * ┌─────────────────────────────────────┐
 * │ CENTER: RTreePanel (트리 그림)        │ EAST: 통계
 * │                                     │
 * ├─────────────────────────────────────┤
 * │ SOUTH: 로그 영역 (텍스트 출력)         │
 * └─────────────────────────────────────┘
 */
public class RTreeVisualizer extends JFrame {

	// ===== 멤버 변수 =====
	private RTreeImpl tree; // 시각화할 R-Tree
	private RTreePanel canvas; // 트리를 그리는 캔버스
	private JTextArea logArea; // 로그 메시지 출력 영역
	private JLabel statsLabel; // 통계 정보 레이블

	/**
	 * 생성자: RTreeVisualizer 초기화 및 GUI 구성
	 *
	 * @param tree 시각화할 R-Tree 객체
	 */
	public RTreeVisualizer(RTreeImpl tree) {
		this.tree = tree;

		// ===== JFrame 기본 설정 =====
		setTitle("4-way R-Tree Visualization"); // 윈도우 제목
		setSize(1000, 900); // 윈도우 크기 (가로×세로 픽셀)

		// X 버튼 클릭 시 동작 설정
		// EXIT_ON_CLOSE: 윈도우 닫으면 프로그램 종료
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// BorderLayout 사용: 5개 영역으로 나눔
		setLayout(new BorderLayout());

		// ===== CENTER: 트리 그리기 캔버스 =====
		canvas = new RTreePanel(tree);

		// JScrollPane: 스크롤 기능 추가
		// 트리가 커지면 스크롤해서 볼 수 있음
		add(new JScrollPane(canvas), BorderLayout.CENTER);

		// ===== SOUTH: 로그 출력 영역 =====
		// JTextArea: 여러 줄 텍스트 표시 가능
		// 파라미터: (줄 수, 컬럼 수)
		logArea = new JTextArea(10, 80);

		// setEditable(false): 사용자가 직접 수정 불가
		// 프로그램만 텍스트 추가 가능
		logArea.setEditable(false);

		// Font 설정: (폰트명, 스타일, 크기)
		// Monospaced: 고정폭 폰트 (코딩용)
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

		// 스크롤 기능 추가
		JScrollPane logScroll = new JScrollPane(logArea);
		add(logScroll, BorderLayout.SOUTH);

		// ===== EAST: 통계 정보 표시 =====
		JPanel rightPanel = new JPanel(); // 패널 생성
		rightPanel.setLayout(new BorderLayout());

		// JLabel: 텍스트나 이미지 표시
		// HTML 사용 가능 (<html>...</html>)
		statsLabel = new JLabel();

		// 수직 정렬: 상단에 붙이기
		statsLabel.setVerticalAlignment(SwingConstants.TOP);

		rightPanel.add(statsLabel, BorderLayout.NORTH);
		add(rightPanel, BorderLayout.EAST);

		// ===== 초기 통계 업데이트 =====
		updateStats();
	}

	/**
	 * updateTree: 트리 업데이트 및 다시 그리기
	 *
	 * 호출 시점:
	 * - Point 추가/삭제 후
	 * - 트리 구조 변경 후
	 *
	 * 동작:
	 * 1. canvas.repaint() → 화면 다시 그리기 요청
	 * 2. updateStats() → 통계 정보 갱신
	 */
	public void updateTree() {
		// Swing에게 "canvas를 다시 그려달라" 요청
		// → paintComponent()가 호출됨
		canvas.repaint();

		// 통계 정보 갱신
		updateStats();
	}

	/**
	 * showStep: 로그 영역에 메시지 추가
	 *
	 * 용도:
	 * - 알고리즘 진행 상황 표시
	 * - 디버깅 정보 출력
	 *
	 * @param message 출력할 메시지
	 */
	public void showStep(String message) {
		// append(): 기존 텍스트 뒤에 추가
		// "\n": 줄바꿈 문자 (새 줄로 이동)
		logArea.append(message + "\n");

		// setCaretPosition(): 커서 위치 이동
		// getDocument().getLength(): 텍스트의 끝 위치
		// → 자동 스크롤 효과 (항상 최신 메시지 보임)
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	/**
	 * highlightPoint: 특정 Point를 강조 표시
	 *
	 * 용도:
	 * - 최근 추가된 Point 강조
	 * - KNN 결과 강조
	 *
	 * @param point 강조할 점
	 */
	public void highlightPoint(Point point) {
		// 캔버스에 강조할 점 설정
		canvas.setHighlightPoint(point);

		// 다시 그리기 → 노란색 원으로 표시됨
		canvas.repaint();
	}

	/**
	 * updateStats: 통계 정보 갱신
	 *
	 * HTML 사용:
	 * - JLabel은 HTML 렌더링 지원
	 * - <html>로 시작하면 HTML로 해석됨
	 * - <br>: 줄바꿈, <h3>: 제목
	 */
	private void updateStats() {
		// StringBuilder: 문자열을 효율적으로 조합
		// String + 연산보다 빠름 (반복 작업 시)
		StringBuilder stats = new StringBuilder("<html>");

		// HTML 태그로 꾸미기
		stats.append("<h3>Tree Statistics</h3>");
		stats.append("Points: ").append(tree.getSize()).append("<br>");
		stats.append("Height: ").append(tree.getHeight()).append("<br>");
		stats.append("</html>");

		// 레이블 텍스트 설정
		// toString(): StringBuilder → String 변환
		statsLabel.setText(stats.toString());
	}

	/**
	 * sleep: 지정된 시간만큼 대기 (애니메이션 효과)
	 *
	 * 용도:
	 * - Point 추가 시 잠시 멈춰서 사용자가 변화를 볼 수 있게 함
	 * - 너무 빠르면 변화를 알아차리기 어려움
	 *
	 * @param millis 대기 시간 (밀리초, 1000ms = 1초)
	 */
	public void sleep(int millis) {
		try {
			// Thread.sleep(): 현재 스레드를 지정된 시간만큼 멈춤
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// InterruptedException: 다른 스레드가 이 스레드를 깨웠을 때 발생
			// printStackTrace(): 에러 정보 출력
			e.printStackTrace();
		}
	}
}
