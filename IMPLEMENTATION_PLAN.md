# 4-way R-Tree 구현 계획서 및 팀 프로젝트 가이드

## 📌 프로젝트 개요

### 기본 정보
- **프로젝트명**: 4-way R-Tree Implementation
- **언어**: Java 17
- **빌드 도구**: Maven
- **GUI**: Java Swing

- **제약사항**:
  - 4-way R-Tree (각 노드 최대 4개 자식)
  - 기존 R-Tree 라이브러리 사용 금지
  - 최소한의 dependency만 사용

### 요구사항 (총 20점)
1. **Task 1 (5점)**: Point 삽입 + 실시간 GUI 시각화
2. **Task 2 (5점)**: 범위 검색 + 가지치기 시각화
3. **Task 3 (5점)**: KNN 검색 + 단계별 과정 시각화
4. **Task 4 (5점)**: Point 삭제 + MBR 갱신 시각화

### 발표 요구사항 (총 10점)
1. **R-Tree 설명 (5점)**: 설계 방향, 코드 설명, 알고리즘 설명
2. **성능 평가 (5점)**: R-Tree 사용 vs 미사용 성능 비교 (Task 2 or 3)

---

---

## 🏗️ 클래스 구조 설계

### 1. 기존 클래스
- [x] `Point.java` - 완성
- [ ] `Rectangle.java` - 확장 필요
- [x] `RTree.java` - 인터페이스 완성
- [ ] `RTreeImpl.java` - 구현 필요
- [x] `Assignment45.java` - 테스트 코드

### 2. 추가 필요 클래스

#### 2.1 RTreeNode (추상 클래스)
```java
abstract class RTreeNode {
    Rectangle mbr;              // Minimum Bounding Rectangle
    RTreeNode parent;           // 부모 노드
    boolean isLeaf;             // 리프 노드 여부

    abstract int getChildCount();
    abstract Rectangle calculateMBR();
    abstract boolean isOverflow();
    abstract boolean isUnderflow();
}
```

#### 2.2 LeafNode (리프 노드)
```java
class LeafNode extends RTreeNode {
    List<Point> points;         // 실제 데이터 포인트들
    static final int MAX_ENTRIES = 4;
    static final int MIN_ENTRIES = 2;

    LeafNode();
    void addPoint(Point p);
    void removePoint(Point p);
    List<Point> getPoints();
}
```

#### 2.3 InternalNode (내부 노드)
```java
class InternalNode extends RTreeNode {
    List<RTreeNode> children;   // 자식 노드들
    static final int MAX_CHILDREN = 4;
    static final int MIN_CHILDREN = 2;

    InternalNode();
    void addChild(RTreeNode child);
    void removeChild(RTreeNode child);
    List<RTreeNode> getChildren();
}
```

#### 2.4 Entry (KNN용 우선순위 큐 엔트리)
```java
class Entry implements Comparable<Entry> {
    Object data;                // Point 또는 RTreeNode
    double distance;            // 거리

    Entry(Object data, double distance);
    boolean isPoint();
    boolean isNode();
    int compareTo(Entry other);
}
```

#### 2.5 RTreeVisualizer (GUI)
```java
class RTreeVisualizer extends JFrame {
    RTreePanel canvas;
    JPanel controlPanel;
    JTextArea logArea;

    RTreeVisualizer(RTreeImpl tree);
    void updateTree();
    void highlightSearch(Rectangle searchArea);
    void highlightKNN(Point source, List<Point> neighbors);
    void showStep(String message);
}
```

#### 2.6 RTreePanel (Canvas)
```java
class RTreePanel extends JPanel {
    RTreeImpl tree;
    Rectangle searchArea;
    Set<RTreeNode> visitedNodes;
    Set<RTreeNode> prunedNodes;

    void paintComponent(Graphics g);
    void drawNode(Graphics2D g2, RTreeNode node, int level);
    void drawPoint(Graphics2D g2, Point p, Color color);
    void drawRectangle(Graphics2D g2, Rectangle r, Color color);
    Point dataToScreen(Point p);
}
```

---

## 📐 Rectangle 클래스 확장

### 추가할 메서드 목록

- [ ] `boolean contains(Point p)` - 점이 사각형 내부에 있는지 확인 (경계 포함)
- [ ] `boolean intersects(Rectangle other)` - 두 사각형이 겹치는지 확인
- [ ] `double area()` - 사각형의 면적 계산
- [ ] `double enlargement(Point p)` - 점을 포함했을 때 면적 증가량
- [ ] `Rectangle expandToInclude(Point p)` - 점을 포함하는 확장된 사각형 반환
- [ ] `Rectangle merge(Rectangle other)` - 두 사각형을 포함하는 MBR
- [ ] `double minDistance(Point p)` - 사각형에서 점까지의 최소 거리 (MINDIST)
- [ ] `static Rectangle createMBR(List<Point> points)` - Point 배열로부터 MBR 생성
- [ ] `boolean equals(Object obj)` - equals 메서드 오버라이드

<details>
<summary>구현 코드 예시 (클릭하여 확장)</summary>

```java
/**
 * 점이 사각형 내부에 있는지 확인 (경계 포함)
 */
public boolean contains(Point p) {
    return p.getX() >= leftTop.getX() &&
           p.getX() <= rightBottom.getX() &&
           p.getY() >= leftTop.getY() &&
           p.getY() <= rightBottom.getY();
}

/**
 * 두 사각형이 겹치는지 확인
 */
public boolean intersects(Rectangle other) {
    return !(other.rightBottom.getX() < this.leftTop.getX() ||
             other.leftTop.getX() > this.rightBottom.getX() ||
             other.rightBottom.getY() < this.leftTop.getY() ||
             other.leftTop.getY() > this.rightBottom.getY());
}

/**
 * 사각형의 면적 계산
 */
public double area() {
    double width = rightBottom.getX() - leftTop.getX();
    double height = rightBottom.getY() - leftTop.getY();
    return width * height;
}

/**
 * 점을 포함했을 때 면적 증가량
 */
public double enlargement(Point p) {
    double newMinX = Math.min(leftTop.getX(), p.getX());
    double newMinY = Math.min(leftTop.getY(), p.getY());
    double newMaxX = Math.max(rightBottom.getX(), p.getX());
    double newMaxY = Math.max(rightBottom.getY(), p.getY());

    double newArea = (newMaxX - newMinX) * (newMaxY - newMinY);
    return newArea - this.area();
}

/**
 * 사각형에서 점까지의 최소 거리 (MINDIST)
 */
public double minDistance(Point p) {
    double dx = 0;
    double dy = 0;

    if (p.getX() < leftTop.getX()) {
        dx = leftTop.getX() - p.getX();
    } else if (p.getX() > rightBottom.getX()) {
        dx = p.getX() - rightBottom.getX();
    }

    if (p.getY() < leftTop.getY()) {
        dy = leftTop.getY() - p.getY();
    } else if (p.getY() > rightBottom.getY()) {
        dy = p.getY() - rightBottom.getY();
    }

    return Math.sqrt(dx * dx + dy * dy);
}
```

</details>

---

## 📝 Task 1: 삽입 연산 구현 (5점)

### ✅ 현재 상태: **완료** (검증 필요)

Task 1은 이미 `RTreeImpl.java`에 구현되어 있습니다. 팀원은 다음 작업을 수행해야 합니다:

### 검증 및 개선 체크리스트

#### 1.1 기능 검증
- [x] add() 메서드 구현 완료 (RTreeImpl.java:80-138)
- [x] 중복 Point 체크 로직 (contains 메서드)
- [x] 빈 트리 처리 (첫 삽입)
- [x] ChooseLeaf 알고리즘 (210-256)
- [x] SplitNode 알고리즘 (261-334)
- [x] AdjustTree 알고리즘 (453-494)
- [ ] **테스트 필요**: 30개 Point 삽입이 정상 작동하는지 확인
- [ ] **디버깅**: MBR이 올바르게 계산되는지 검증
- [ ] **예외 처리**: null 체크 및 에러 처리 추가

#### 1.2 ChooseLeaf 알고리즘
- [ ] 리프 노드 도달 시 반환
- [ ] 면적 증가 최소화 자식 선택
- [ ] 동점 시 면적이 작은 자식 선택
- [ ] 재귀 호출

#### 1.3 SplitNode 알고리즘
- [ ] PickSeeds: X/Y축 기준 가장 먼 두 엔트리 선택
- [ ] 두 그룹 초기화
- [ ] 나머지 엔트리 분배
- [ ] 최소 개수(MIN_ENTRIES) 보장
- [ ] 면적 증가 최소화
- [ ] 새 노드 생성 및 반환

#### 1.4 AdjustTree 알고리즘
- [ ] 리프부터 루트까지 MBR 갱신
- [ ] 새 노드 부모에 추가
- [ ] 부모 오버플로우 시 재귀 분할
- [ ] 루트 분할 시 새 루트 생성 (높이 증가)

#### 1.5 GUI 시각화
- [ ] JFrame 메인 윈도우 생성
- [ ] JPanel Canvas 구현
- [ ] 좌표 변환 (데이터 → 화면)
- [ ] Point 그리기 (빨간 점)
- [ ] 리프 MBR 그리기 (파란 실선)
- [ ] 내부 노드 MBR 그리기 (초록 점선)
- [ ] 레벨별 색상 구분
- [ ] 현재 추가된 Point 강조
- [ ] 단계별 애니메이션

### 핵심 알고리즘 의사코드

<details>
<summary>ChooseLeaf 의사코드</summary>

```java
LeafNode chooseLeaf(RTreeNode N, Point p) {
    if (N is leaf) return N;

    // 면적 증가가 최소인 자식 찾기
    Node bestChild = null;
    double minEnlargement = Double.MAX_VALUE;

    for (child in N.children) {
        double enlargement = child.mbr.enlargement(p);
        if (enlargement < minEnlargement) {
            minEnlargement = enlargement;
            bestChild = child;
        }
        // 동점이면 면적이 작은 것 선택
        else if (enlargement == minEnlargement) {
            if (child.mbr.area() < bestChild.mbr.area()) {
                bestChild = child;
            }
        }
    }

    return chooseLeaf(bestChild, p);
}
```

</details>

---

## 🔍 Task 2: 범위 검색 구현 (5점)

### 구현 체크리스트

#### 2.1 search() 메서드
- [ ] 결과 리스트 초기화
- [ ] 재귀 탐색 시작
- [ ] Iterator 반환
- [ ] GUI 업데이트

#### 2.2 searchRecursive() 메서드
- [ ] MBR과 검색 범위 겹침 체크
- [ ] 안 겹치면 가지치기 (pruning)
- [ ] 리프 노드: Point들 범위 체크
- [ ] 내부 노드: 자식들 재귀 탐색
- [ ] 방문/가지치기 노드 기록

#### 2.3 GUI 시각화
- [ ] 검색 범위 사각형 그리기 (굵은 빨간선)
- [ ] 방문한 MBR 강조 (초록)
- [ ] 가지치기된 MBR 흐리게 (회색)
- [ ] 발견된 Point 강조 (빨간 큰 원)
- [ ] 단계별 진행 표시

### 예상 출력
```
검색 범위: (0,0) ~ (100,100)
예상 결과: 11개 Point

Point [x=45.0, y=15.0]
Point [x=35.0, y=20.0]
Point [x=25.0, y=25.0]
Point [x=20.0, y=30.0]
Point [x=28.0, y=32.0]
Point [x=15.0, y=45.0]
Point [x=30.0, y=40.0]
Point [x=40.0, y=35.0]
Point [x=70.0, y=80.0]
Point [x=95.0, y=90.0]
Point [x=100.0, y=65.0]
```

---

## 🎯 Task 3: KNN 검색 구현 (5점)

### 구현 체크리스트

#### 3.1 nearest() 메서드
- [ ] PriorityQueue 생성 (거리 기준)
- [ ] Entry 클래스 구현
- [ ] 루트를 PQ에 추가 (MINDIST)
- [ ] Best-First 알고리즘
- [ ] k개 결과 수집
- [ ] 결과 Iterator 반환

#### 3.2 Best-First 알고리즘
- [ ] PQ에서 가장 가까운 엔트리 추출
- [ ] Point면 → 결과에 추가
- [ ] 리프 노드면 → 모든 Point를 PQ에 추가
- [ ] 내부 노드면 → 모든 자식을 PQ에 추가
- [ ] k개 수집될 때까지 반복

#### 3.3 MINDIST 계산
- [ ] 점이 사각형 내부에 있으면 0
- [ ] 점이 사각형 밖에 있으면 가장 가까운 경계까지 거리
- [ ] X축, Y축 각각 계산 후 유클리드 거리

#### 3.4 GUI 시각화
- [ ] source Point 표시 (큰 파란 원)
- [ ] 발견된 KNN 표시 (빨간 원 + 번호)
- [ ] source부터 KNN까지 선 그리기
- [ ] 거리 텍스트 표시
- [ ] 탐색 과정 단계별 표시
- [ ] 현재 탐색 중인 MBR 강조

### 예상 출력
```
Source: (75, 85)
K = 5

Point [x=70.0, y=80.0]:7.0710678118654755
Point [x=95.0, y=90.0]:20.615528128088304
Point [x=80.0, y=110.0]:25.495097567963924
Point [x=100.0, y=65.0]:32.01562118716424
Point [x=120.0, y=100.0]:47.43416490252569
```

---

## 🗑️ Task 4: 삭제 연산 구현 (5점)

### 구현 체크리스트

#### 4.1 delete() 메서드
- [ ] FindLeaf로 Point 찾기
- [ ] 리프에서 Point 제거
- [ ] CondenseTree 호출
- [ ] 제거된 노드의 엔트리들 재삽입
- [ ] 루트 처리 (자식 1개면 높이 감소)
- [ ] isEmpty() 처리
- [ ] GUI 업데이트

#### 4.2 FindLeaf 알고리즘
- [ ] 리프 노드 도달 시 Point 확인
- [ ] MBR에 포함되지 않으면 가지치기
- [ ] 재귀 탐색
- [ ] 발견한 리프 반환

#### 4.3 CondenseTree 알고리즘
- [ ] 리프부터 루트까지 올라가며
- [ ] 언더플로우 체크 (< MIN_ENTRIES)
- [ ] 언더플로우면 노드 제거, 엔트리 저장
- [ ] 그렇지 않으면 MBR 갱신
- [ ] 제거된 엔트리들 재삽입

#### 4.4 재삽입 로직
- [ ] 리프 엔트리면 add() 호출
- [ ] 내부 노드 엔트리면 적절한 레벨에 삽입

#### 4.5 GUI 시각화
- [ ] 삭제할 Point 강조 (빨간 X)
- [ ] 언더플로우 노드 표시 (빨간 점선)
- [ ] 재삽입 엔트리 표시 (노란 점선)
- [ ] 갱신된 MBR 애니메이션
- [ ] 최종 트리 구조 표시
- [ ] isEmpty() 시 "Empty Tree" 메시지

### 특수 케이스 처리
- [ ] 존재하지 않는 Point 삭제 시도
- [ ] 마지막 Point 삭제 → isEmpty() == true
- [ ] 루트만 남고 자식 1개 → 높이 감소
- [ ] 연쇄 언더플로우 처리

---

## 🎨 GUI 상세 설계

### 전체 레이아웃

```
┌─────────────────────────────────────────────────────┐
│  [Task1] [Task2] [Task3] [Task4] [Reset] [Next]    │ ← 컨트롤 패널
├─────────────────────────────────────────────────────┤
│                                                     │
│                                                     │
│              Canvas (800x800)                       │ ← 트리 시각화
│                                                     │
│                                                     │
├─────────────────────────────────────────────────────┤
│ 통계: 높이=3, 노드=15, 포인트=30                      │ ← 상태 표시
│ 로그: Added point (20, 30)...                       │ ← 로그 영역
└─────────────────────────────────────────────────────┘
```


### 좌표 변환
```java
데이터 범위: (0, 0) ~ (200, 200)
화면 크기: 800x800
스케일: 4배
여백: 50px

screenX = MARGIN + dataX * SCALE
screenY = MARGIN + dataY * SCALE
```

### GUI 구현 체크리스트
- [ ] RTreeVisualizer 클래스
- [ ] RTreePanel 클래스
- [ ] 컨트롤 패널 (버튼들)
- [ ] 로그 영역 (JTextArea)
- [ ] 통계 레이블
- [ ] paintComponent() 구현
- [ ] 좌표 변환 메서드
- [ ] 레이어별 그리기
- [ ] 애니메이션 효과



## 📊 구현 진행 상황

### Phase 1: 핵심 자료구조 
- [ ] Rectangle 클래스 확장 (메서드 추가)
- [ ] RTreeNode 추상 클래스
- [ ] LeafNode 클래스
- [ ] InternalNode 클래스
- [ ] Entry 클래스 (KNN용)

### Phase 2: 삽입 + Task 1 
- [ ] add() 메서드 구현
- [ ] ChooseLeaf 알고리즘
- [ ] SplitNode 알고리즘 (LinearSplit)
- [ ] AdjustTree 알고리즘
- [ ] isEmpty() 구현
- [ ] GUI 기본 프레임워크 (Swing)
- [ ] 삽입 시각화 구현
- [ ] 단계별 진행 기능

### Phase 3: 검색 + Task 2 
- [ ] search() 메서드 구현
- [ ] 재귀 탐색 알고리즘
- [ ] 검색 범위 시각화
- [ ] 가지치기 하이라이트

### Phase 4: KNN + Task 3 
- [ ] nearest() 메서드 구현
- [ ] PriorityQueue 기반 Best-First
- [ ] MINDIST 계산
- [ ] KNN 과정 시각화
- [ ] 단계별 탐색 표시

### Phase 5: 삭제 + Task 4
- [ ] delete() 메서드 구현
- [ ] FindLeaf 알고리즘
- [ ] CondenseTree 알고리즘
- [ ] 재삽입 로직
- [ ] 삭제 시각화
- [ ] 최종 테스트

### Phase 6: 통합 및 최적화 
- [ ] 모든 Task 통합 테스트
- [ ] GUI 개선 및 버그 수정
- [ ] 주석 및 문서화
- [ ] 최종 검증
- [ ] 성능 최적화

---

## 💡 구현 팁 및 주의사항

### 핵심 주의사항
1. **4-way 제약 준수**: 모든 노드는 최대 4개의 자식/엔트리만 가능
2. **최소 개수 유지**: 루트를 제외한 모든 노드는 최소 2개 이상
3. **MBR 갱신**: 삽입/삭제 시 모든 조상 노드의 MBR 갱신 필수
4. **중복 방지**: 같은 좌표의 Point는 하나만 존재
5. **GUI 동기화**: 트리 변경 시 반드시 GUI 업데이트
6. **예외 처리**: null 체크, 빈 트리 처리 등

### 최적화 팁
1. **Split 알고리즘**: LinearSplit이 가장 간단, QuadraticSplit이 더 효율적
2. **캐싱**: MBR 계산 결과 캐싱으로 성능 향상
3. **메모리 관리**: 불필요한 객체 생성 최소화
4. **GUI 성능**: repaint() 호출 최소화, 더블 버퍼링 활용
5. **로깅**: 디버깅용 로그 레벨 조절 가능하게

### 자주 발생하는 오류
1. **NullPointerException**: 부모 참조, MBR null 체크
2. **IndexOutOfBoundsException**: 자식/엔트리 리스트 접근 시
3. **StackOverflowError**: 재귀 깊이 확인
4. **무한 루프**: 분할/재삽입 로직 확인
5. **잘못된 MBR**: 좌상단/우하단 좌표 순서 확인

---

### 핵심 개념
- **MBR (Minimum Bounding Rectangle)**: 모든 객체를 포함하는 최소 사각형
- **ChooseLeaf**: 삽입 시 적절한 리프 노드 선택 (면적 증가 최소화)
- **SplitNode**: 오버플로우 시 노드 분할 (LinearSplit, QuadraticSplit)
- **AdjustTree**: 삽입 후 조상 노드들의 MBR 갱신
- **CondenseTree**: 삭제 시 언더플로우 처리 및 재삽입
- **MINDIST**: KNN 검색을 위한 사각형-점 최소 거리

### 시간 복잡도
- **삽입**: O(log n) ~ O(n) (평균: O(log n))
- **검색**: O(√n) ~ O(n) (최악의 경우)
- **KNN**: O(k log n) (Best-First 알고리즘)
- **삭제**: O(log n) + 재삽입 비용

### 공간 복잡도
- **총 노드 수**: O(n / M) where M = MAX_ENTRIES
- **트리 높이**: O(log_M n)

---

## 📈 성공 기준

### Task 1 (5점)
✅ 30개 Point가 순차적으로 삽입됨
✅ GUI에서 각 삽입 단계가 시각화됨
✅ 계층적 MBR이 올바르게 표시됨
✅ 4-way 제약 (최대 4개 자식) 준수
✅ MBR이 모든 Point를 올바르게 포함

### Task 2 (5점)
✅ (0,0,100,100) 범위 내 11개 Point 정확히 검색
✅ 검색 범위가 GUI에 명확히 표시됨
✅ 가지치기된 영역이 시각적으로 구분됨
✅ 올바른 결과 출력 (순서 무관)
✅ 경계 케이스 올바르게 처리

### Task 3 (5점)
✅ (75,85)에서 가까운 5개 Point 정확히 검색
✅ 거리 순서대로 정렬됨
✅ 탐색 과정이 단계별로 표시됨
✅ 올바른 거리 계산 (소수점 2자리)
✅ 시각적으로 결과 확인 가능

### Task 4 (5점)
✅ 30개 Point가 순차적으로 삭제됨
✅ 각 삭제 단계가 시각화됨
✅ 최종적으로 isEmpty() == true
✅ MBR이 올바르게 갱신됨
✅ 언더플로우 처리 올바름

---
