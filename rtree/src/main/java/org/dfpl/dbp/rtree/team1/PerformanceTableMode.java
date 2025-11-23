package org.dfpl.dbp.rtree.team1;

/**
 * R-Tree 성능 비교 - 표 모드
 * 
 * ▶ 실행 방법: 이 파일을 열고 Ctrl+F11 또는 Run 버튼 클릭
 * 
 * ▶ 기능:
 * - 여러 데이터 크기 (100, 500, 1000, 5000, 10000)에 대해 성능 비교
 * - 범위 검색과 kNN 검색을 각각 별도 표로 출력
 * - 각 측정은 50회 반복의 중앙값 사용
 */
public class PerformanceTableMode {
    public static void main(String[] args) {
        PerformanceComparison.runTableMode();
    }
}
