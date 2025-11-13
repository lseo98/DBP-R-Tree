package org.dfpl.dbp.rtree;

import java.util.List;

public class Rectangle {

    // ===== 멤버 변수 =====
    // leftTop: 직사각형의 좌상단 점 (x 최소값, y 최소값)
    // rightBottom: 직사각형의 우하단 점 (x 최대값, y 최대값)
    //두 점으로 사각형 표현 가능
    private Point leftTop;
    private Point rightBottom;

    /**
     * 생성자: Rectangle 객체를 생성
     *
     * @param leftTop 좌상단 점 (minX, minY)
     * @param rightBottom 우하단 점 (maxX, maxY)
     *
     * 예시:
     * Rectangle rect = new Rectangle(new Point(0, 0), new Point(100, 100));
     * → (0,0)부터 (100,100)까지의 정사각형
     */

    public Rectangle(Point leftTop, Point rightBottom) {
        super(); // Object 클래스의 생성자 호출 (Java 기본)
        this.leftTop = leftTop; // this는 현재 객체를 가리킴
        this.rightBottom = rightBottom;
    }

    public Point getLeftTop() {
        return leftTop;
    }

    public void setLeftTop(Point leftTop) {
        this.leftTop = leftTop;
    }

    public Point getRightBottom() {
        return rightBottom;
    }

    public void setRightBottom(Point rightBottom) {
        this.rightBottom = rightBottom;
    }

    /**
     * contains: 점이 사각형 내부에 있는지 확인 (경계선 위도 포함)
     *
     * R-Tree에서 사용 예:
     * - 검색 시: 점이 검색 범위 내에 있는지 확인
     * - 삽입 시: 점이 어떤 MBR에 속하는지 확인
     *
     * @param p 확인할 점
     * @return true면 점이 사각형 내부에 있음, false면 외부
     *
     * 예시:
     * Rectangle rect = new Rectangle(Point(0,0), Point(100,100));
     * rect.contains(Point(50,50))  → true  (내부)
     * rect.contains(Point(100,100)) → true  (경계선, >= 과 <= 사용으로 포함)
     * rect.contains(Point(150,50))  → false (외부)
     */

    public boolean contains(Point p) {
        // 4가지 조건을 모두 만족해야 내부에 있음
        // 1. p.x >= leftTop.x (점이 왼쪽 경계보다 오른쪽)
        // 2. p.x <= rightBottom.x (점이 오른쪽 경계보다 왼쪽)
        // 3. p.y >= leftTop.y (점이 위쪽 경계보다 아래)
        // 4. p.y <= rightBottom.y (점이 아래쪽 경계보다 위)
        // && 는 AND 연산자: 모든 조건이 true여야 결과가 true
        return p.getX() >= leftTop.getX() && p.getX() <= rightBottom.getX() && p.getY() >= leftTop.getY()
                && p.getY() <= rightBottom.getY();
    }

    /**
     * intersects: 두 사각형이 겹치는지 확인
     *
     * R-Tree에서 사용 예:
     * - 검색 시: 노드의 MBR이 검색 범위와 겹치는지 확인 (가지치기)
     * - 겹치지 않으면 그 노드는 탐색할 필요 없음 (pruning)
     *
     * @param other 비교할 다른 사각형
     * @return true면 두 사각형이 겹침, false면 완전히 분리됨
     *
     * 알고리즘 설명:
     * "겹친다"를 직접 체크하기는 복잡함
     * → "겹치지 않는다"를 체크한 후 부정(!)하면 간단함
     *
     * 두 사각형이 겹치지 않는 4가지 경우:
     * 1. other가 this의 왼쪽에 완전히 떨어져 있음
     * 2. other가 this의 오른쪽에 완전히 떨어져 있음
     * 3. other가 this의 위쪽에 완전히 떨어져 있음
     * 4. other가 this의 아래쪽에 완전히 떨어져 있음
     */
    public boolean intersects(Rectangle other) {
        // 겹치지 않는 조건들을 || (OR)로 연결
        // 하나라도 true면 겹치지 않음
        boolean notIntersect = other.rightBottom.getX() < this.leftTop.getX() // other가 왼쪽에
                || other.leftTop.getX() > this.rightBottom.getX() // other가 오른쪽에
                || other.rightBottom.getY() < this.leftTop.getY() // other가 위쪽에
                || other.leftTop.getY() > this.rightBottom.getY(); // other가 아래쪽에

        // ! 는 NOT 연산자: "겹치지 않는다"의 반대 = "겹친다"
        return !notIntersect;
    }

    /**
     * area: 사각형의 면적 계산
     *
     * R-Tree에서 사용 예:
     * - Split 시 동점일 때: 면적이 작은 노드를 선택
     * - 면적이 작을수록 공간을 효율적으로 사용함
     *
     * @return 사각형의 면적 (가로 × 세로)
     */
    public double area() {
        // 가로 길이 = 오른쪽 x - 왼쪽 x
        double width = rightBottom.getX() - leftTop.getX();
        // 세로 길이 = 아래쪽 y - 위쪽 y
        double height = rightBottom.getY() - leftTop.getY();
        // 면적 = 가로 × 세로
        return width * height;
    }

    /**
     * enlargement: 점을 포함했을 때 면적이 얼마나 증가하는지 계산
     *
     * ★★★ R-Tree의 핵심 메서드 ★★★
     * ChooseLeaf 알고리즘의 핵심: "면적 증가가 최소인 자식을 선택"
     *
     * R-Tree에서 사용 예:
     * - 삽입 시: 어느 자식 노드에 추가할지 결정
     * - Split 시: 어느 그룹에 배치할지 결정
     *
     * @param p 추가할 점
     * @return 면적 증가량 (항상 >= 0)
     *
     * 알고리즘 단계:
     * 1. 현재 MBR의 면적 계산
     * 2. 점 p를 포함하도록 확장된 MBR의 면적 계산
     * 3. 증가량 = 새 면적 - 현재 면적
     *
     * 예시:
     * 현재 MBR: (10,10) ~ (20,20) → 면적 100
     * 새 점: (25,15)
     * 확장된 MBR: (10,10) ~ (25,20) → 면적 150
     * 증가량: 50
     */
    public double enlargement(Point p) {
        // 단계 1: 점 p를 포함하는 새로운 경계 계산

        // Math.min(): 두 값 중 작은 값 반환
        // leftTop은 최소값들이므로 min 사용
        double newMinX = Math.min(leftTop.getX(), p.getX());
        double newMinY = Math.min(leftTop.getY(), p.getY());

        // Math.max(): 두 값 중 큰 값 반환
        // rightBottom은 최대값들이므로 max 사용
        double newMaxX = Math.max(rightBottom.getX(), p.getX());
        double newMaxY = Math.max(rightBottom.getY(), p.getY());

        // 단계 2: 확장된 사각형의 면적 계산
        double newArea = (newMaxX - newMinX) * (newMaxY - newMinY);

        // 단계 3: 증가량 = 새 면적 - 현재 면적
        // this.area()는 현재 객체의 area() 메서드 호출
        return newArea - this.area();
    }

    /**
     * 점을 포함하는 확장된 사각형 반환
     */
    public Rectangle expandToInclude(Point p) {
        double newMinX = Math.min(leftTop.getX(), p.getX());
        double newMinY = Math.min(leftTop.getY(), p.getY());
        double newMaxX = Math.max(rightBottom.getX(), p.getX());
        double newMaxY = Math.max(rightBottom.getY(), p.getY());

        return new Rectangle(new Point(newMinX, newMinY), new Point(newMaxX, newMaxY));
    }

    /**
     * 두 사각형을 포함하는 MBR
     */
    public Rectangle merge(Rectangle other) {
        double newMinX = Math.min(this.leftTop.getX(), other.leftTop.getX());
        double newMinY = Math.min(this.leftTop.getY(), other.leftTop.getY());
        double newMaxX = Math.max(this.rightBottom.getX(), other.rightBottom.getX());
        double newMaxY = Math.max(this.rightBottom.getY(), other.rightBottom.getY());

        return new Rectangle(new Point(newMinX, newMinY), new Point(newMaxX, newMaxY));
    }

    /**
     * minDistance: 사각형에서 점까지의 최소 거리 (MINDIST)
     *
     * ★★★ KNN (K-Nearest Neighbor) 검색의 핵심 메서드 ★★★
     *
     * R-Tree에서 사용 예:
     * - KNN 검색 시: 노드의 MBR과 검색 점 사이의 최소 거리 계산
     * - 우선순위 큐에 넣을 때 이 거리를 기준으로 정렬
     * - 거리가 가까운 노드부터 탐색 (Best-First Search)
     *
     * @param p 기준점 (검색의 중심점)
     * @return 사각형에서 점까지의 최소 거리
     *
     * 3가지 케이스:
     * 1. 점이 사각형 내부: 거리 = 0
     * 2. 점이 사각형 측면 방향: 한 축만 거리 계산 (dx 또는 dy만)
     * 3. 점이 사각형 대각선 방향: 두 축 모두 거리 계산 √(dx² + dy²)
     *
     * 예시:
     * Rectangle: (10,10) ~ (20,20)
     * Point(15,15): 내부 → 거리 0
     * Point(25,15): 오른쪽 → dx=5, dy=0 → 거리 5
     * Point(25,25): 우하단 → dx=5, dy=5 → 거리 7.07
     */
    public double minDistance(Point p) {
        // dx, dy: 각 축에서의 거리 (초기값 0)
        double dx = 0;
        double dy = 0;

        // === X축 방향 거리 계산 ===
        if (p.getX() < leftTop.getX()) {
            // 점이 사각형의 왼쪽에 있음
            // 거리 = 왼쪽 경계 - 점의 x 좌표
            dx = leftTop.getX() - p.getX();
        } else if (p.getX() > rightBottom.getX()) {
            // 점이 사각형의 오른쪽에 있음
            // 거리 = 점의 x 좌표 - 오른쪽 경계
            dx = p.getX() - rightBottom.getX();
        }
        // else: 점이 x축으로는 사각형 범위 내 → dx = 0 유지

        // === Y축 방향 거리 계산 ===
        if (p.getY() < leftTop.getY()) {
            // 점이 사각형의 위쪽에 있음
            dy = leftTop.getY() - p.getY();
        } else if (p.getY() > rightBottom.getY()) {
            // 점이 사각형의 아래쪽에 있음
            dy = p.getY() - rightBottom.getY();
        }
        // else: 점이 y축으로는 사각형 범위 내 → dy = 0 유지

        // === 유클리드 거리 계산 ===
        // Math.sqrt(): 제곱근 함수
        // 피타고라스 정리: 거리 = √(dx² + dy²)
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * createMBR: 여러 점들을 모두 포함하는 MBR 생성 (정적 메서드)
     *
     * static 메서드란?
     * - 객체 없이 클래스 이름으로 직접 호출 가능
     * - 예: Rectangle.createMBR(points)
     * - 유틸리티 함수로 자주 사용됨
     *
     * R-Tree에서 사용 예:
     * - 리프 노드의 MBR 계산: 노드가 가진 모든 Point의 MBR
     * - Split 시 그룹의 MBR 계산
     *
     * @param points 포함할 점들의 리스트
     * @return 모든 점을 포함하는 최소 사각형 (MBR)
     *
     * 알고리즘:
     * 1. 모든 점의 x,y 최소값과 최대값을 찾음
     * 2. (minX, minY) ~ (maxX, maxY) 사각형 생성
     */
    public static Rectangle createMBR(List<Point> points) {
        // === 예외 처리: null 또는 빈 리스트 ===
        // ||는 OR 연산자: 하나라도 true면 true
        if (points == null || points.isEmpty()) {
            return null; // MBR을 만들 수 없음
        }

        // === 초기값 설정 ===
        // Double.MAX_VALUE: double의 최대값 (약 1.7 × 10^308)
        // Double.MIN_VALUE: double의 최소 양수값 (거의 0에 가까움)
        // → 여기서는 Double.NEGATIVE_INFINITY나 -Double.MAX_VALUE가 더 정확하지만
        // 실제 좌표값이 양수라고 가정하면 이 방식도 작동함
        double minX = Double.MAX_VALUE; // 매우 큰 값으로 시작 (나중에 작은 값으로 갱신)
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE; // 매우 작은 값으로 시작 (나중에 큰 값으로 갱신)
        double maxY = Double.MIN_VALUE;

        // === 모든 점을 순회하며 최소/최대값 찾기 ===
        // for-each 문법: List의 모든 요소를 하나씩 가져옴
        for (Point p : points) {
            // Math.min(): 두 값 중 작은 값 반환
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());

            // Math.max(): 두 값 중 큰 값 반환
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }

        // === MBR 생성 및 반환 ===
        // new Point(): 새로운 Point 객체 생성
        // new Rectangle(): 새로운 Rectangle 객체 생성 후 반환
        return new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Rectangle))
            return false;
        Rectangle other = (Rectangle) obj;
        return this.leftTop.getX() == other.leftTop.getX() && this.leftTop.getY() == other.leftTop.getY()
                && this.rightBottom.getX() == other.rightBottom.getX()
                && this.rightBottom.getY() == other.rightBottom.getY();
    }

    @Override
    public String toString() {
        return "Rectangle [leftTop=(" + leftTop.getX() + "," + leftTop.getY() + "), rightBottom=(" + rightBottom.getX()
                + "," + rightBottom.getY() + ")]";
    }
}
