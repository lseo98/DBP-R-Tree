package org.dfpl.dbp.rtree;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * RTreeVisualizer: R-Tree 시각화 메인 윈도우 (Task 1/2 모드 지원)
 *
 * Task 1 (삽입):
 * - 레벨별 색상으로 MBR 표시
 * - 강조 포인트 노란색 표시
 * - 추가 과정 로그 출력
 *
 * Task 2 (탐색):
 * - 검색 범위 노란색으로 표시
 * - 방문한 노드 파란색, 가지치기된 노드 회색
 * - 탐색 통계 (방문/가지치기 노드 수) 표시
 */
public class RTreeVisualizer extends JFrame {

    // ===== 멤버 변수 =====
    private RTreeImpl tree;
    private RTreePanel canvas;
    private JTextArea logArea;
    private JLabel statsLabel;
    private JComboBox<String> taskSelector; // Task 선택 드롭다운

    /**
     * 생성자: RTreeVisualizer 초기화 및 GUI 구성
     */
    public RTreeVisualizer(RTreeImpl tree) {
        this.tree = tree;

        // ===== JFrame 기본 설정 =====
        setTitle("4-way R-Tree Visualization (Task 1/2)");
        setSize(1100, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ===== NORTH: 제어 패널 (Task 선택) =====
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.NORTH);

        // ===== CENTER: 트리 그리기 캔버스 =====
        canvas = new RTreePanel(tree);
        add(new JScrollPane(canvas), BorderLayout.CENTER);

        // ===== SOUTH: 로그 출력 영역 =====
        logArea = new JTextArea(10, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        add(logScroll, BorderLayout.SOUTH);

        // ===== EAST: 통계 정보 표시 =====
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        statsLabel = new JLabel();
        statsLabel.setVerticalAlignment(SwingConstants.TOP);
        rightPanel.add(statsLabel, BorderLayout.NORTH);
        add(rightPanel, BorderLayout.EAST);

        // ===== 초기 설정 =====
        updateStats();
    }

    /**
     * Task 선택 제어 패널 생성
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 라벨
        JLabel label = new JLabel("Select Task Mode:");
        panel.add(label);

        // Task 선택 콤보박스
        String[] tasks = { "Task 1: Insertion (삽입 시각화)", "Task 2: Search (탐색 시각화)" };
        taskSelector = new JComboBox<>(tasks);
        taskSelector.setSelectedIndex(0); // 기본값: Task 1

        // 선택 변경 이벤트
        taskSelector.addActionListener(e -> {
            int selectedIndex = taskSelector.getSelectedIndex();
            if (selectedIndex == 0) {
                canvas.setTaskMode(RTreePanel.TaskMode.TASK_1);
                showStep("[MODE CHANGED] Task 1: Insertion Visualization");
            } else {
                canvas.setTaskMode(RTreePanel.TaskMode.TASK_2);
                showStep("[MODE CHANGED] Task 2: Search Visualization (Ready)");
            }
            canvas.repaint();
        });

        panel.add(taskSelector);

        // 설명 라벨
        JLabel descLabel = new JLabel(
                "Task 1: 레벨별 색상, 강조 포인트 | Task 2: 검색 범위, 방문/가지치기 노드");
        descLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        panel.add(Box.createHorizontalStrut(20)); // 여백
        panel.add(descLabel);

        return panel;
    }

    /**
     * 트리 업데이트 및 화면 갱신
     */
    public void updateTree() {
        canvas.repaint();
        updateStats();
    }

    /**
     * 로그 메시지 출력
     */
    public void showStep(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * 특정 Point 강조 표시 (Task 1용)
     */
    public void highlightPoint(Point point) {
        canvas.setHighlightPoint(point);
        canvas.repaint();
    }

    /**
     * 탐색 범위 시각화 (Task 2용)
     */
    public void setSearchVisualization(Rectangle searchArea, Set<RTreeNode> visited,
                                       Set<RTreeNode> pruned) {
        canvas.setSearchArea(searchArea);
        canvas.setVisitedNodes(visited);
        canvas.setPrunedNodes(pruned);
        canvas.repaint();
        canvas.setSearchResults(tree.getSearchResults());

    }

    /**
     * 통계 정보 갱신
     */
    private void updateStats() {
        StringBuilder stats = new StringBuilder("<html>");
        stats.append("<h3>Tree Statistics</h3>");
        stats.append("Points: ").append(tree.getSize()).append("<br>");
        stats.append("Height: ").append(tree.getHeight()).append("<br>");
        stats.append("<br>");

        // Task 모드별 추가 정보
        int selectedTask = taskSelector != null ? taskSelector.getSelectedIndex() : 0;
        if (selectedTask == 1) {
            // Task 2 정보
            stats.append("<h4>Search Info</h4>");
            stats.append("Search Range: -<br>");
            stats.append("Visited Nodes: -<br>");
            stats.append("Pruned Nodes: -<br>");
        } else {
            // Task 1 정보
            stats.append("<h4>Insert Info</h4>");
            stats.append("Last Point: -<br>");
        }

        stats.append("</html>");
        statsLabel.setText(stats.toString());
    }

    /**
     * 애니메이션 지연 (Task 1 삽입 시 사용)
     */
    public void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

// ===== Set 인터페이스 import를 위해 필요 =====
// import java.util.Set;