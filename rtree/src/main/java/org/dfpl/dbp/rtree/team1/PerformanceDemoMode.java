package org.dfpl.dbp.rtree.team1;

/**
 * R-Tree 성능 비교 - 데모 모드
 * 
 * ▶ 실행 방법: 이 파일을 열고 Ctrl+F11 또는 Run 버튼 클릭
 * 
 * ▶ 기능:
 * - 1000개 포인트로 빠른 성능 테스트
 * - 범위 검색과 kNN 검색 결과를 상세히 출력
 * - k-NN 결과 샘플 (상위 5개) 표시
 */
public class PerformanceDemoMode {
    public static void main(String[] args) {
        PerformanceComparison.runDemoMode();
    }
}
