# R-Tree 구현 요구사항 명세서

## 📋 개요
구현 요구사항 명세서

---

## 🎯 Task 1: 삽입 연산 (5점)

### 현재 상태
✅ **구현 완료** - `RTreeImpl.java`에 구현됨

### 검증 작업
- `Assignment45.java`를 실행하여 30개 Point가 정상적으로 삽입되는지 확인
- GUI에서 각 삽입 단계마다 MBR이 올바르게 표시되는지 확인
- 중복 Point가 추가되지 않는지 확인

---

## 🎯 Task 2: 범위 검색 (5점)

### 구현 위치: `RTreeImpl.java`

```java
/**
 * 범위 검색 (Range Query)
 *
 * 구현 내용:
 * - 주어진 Rectangle 범위 내에 있는 모든 Point를 찾아서 반환
 * - MBR이 검색 범위와 겹치지 않으면 가지치기(Pruning)
 * - 겹치는 MBR만 탐색하여 효율성 향상
 *
 * 필요한 작업:
 * 1. 결과를 저장할 List<Point> 생성
 * 2. 루트부터 재귀적으로 탐색 (searchRecursive 호출)
 * 3. 방문한 노드와 가지치기된 노드를 Set에 기록 (GUI 시각화용)
 * 4. 결과 리스트의 Iterator 반환
 *
 * @param rectangle 검색 범위
 * @return 범위 내의 모든 Point
 */
@Override
public Iterator<Point> search(Rectangle rectangle) {
    // TODO: 구현 필요
    return null;
}

/**
 * 재귀적 범위 검색 헬퍼 메서드
 *
 * 구현 내용:
 * - 현재 노드의 MBR이 검색 범위와 겹치는지 확인 (intersects 사용)
 * - 겹치지 않으면 가지치기 (prunedNodes에 추가 후 return)
 * - 겹치면 방문 노드로 기록 (visitedNodes에 추가)
 * - 리프 노드면: 각 Point가 범위 내에 있는지 확인 (contains 사용) 후 results에 추가
 * - 내부 노드면: 모든 자식에 대해 재귀 호출
 *
 * @param node 현재 탐색 중인 노드
 * @param rectangle 검색 범위
 * @param results 검색 결과를 담을 리스트
 * @param visitedNodes 방문한 노드들 (GUI용)
 * @param prunedNodes 가지치기된 노드들 (GUI용)
 */
private void searchRecursive(RTreeNode node, Rectangle rectangle,
                              List<Point> results,
                              Set<RTreeNode> visitedNodes,
                              Set<RTreeNode> prunedNodes) {
    // TODO: 구현 필요
}
```

### GUI 시각화: `RTreePanel.java`

```java
/**
 * paintComponent에 추가할 시각화 요소:
 *
 * 1. 검색 범위 그리기
 *    - searchArea가 null이 아니면 빨간색 굵은 선으로 표시
 *    - 라벨 "Search Area" 추가
 *
 * 2. 가지치기된 노드 표시
 *    - prunedNodes Set을 순회하며 회색 반투명으로 채우기
 *    - 사용자가 "이 영역은 탐색 안 했다"고 인식할 수 있도록
 *
 * 3. 방문한 노드 강조
 *    - visitedNodes Set을 순회하며 초록색 테두리로 강조
 *    - 사용자가 "이 영역은 탐색했다"고 인식할 수 있도록
 */
```

### 예상 결과
- 검색 범위: `(0,0) ~ (100,100)`
- 결과: 11개 Point (순서 무관)

---

## 🎯 Task 3: KNN 검색 (5점)

### 추가 클래스 필요: `Entry.java`

```java
/**
 * KNN 검색을 위한 우선순위 큐 엔트리 클래스
 *
 * 구현 내용:
 * - Point 또는 RTreeNode를 담을 수 있는 래퍼 클래스
 * - 거리(distance)를 기준으로 정렬 (Comparable 구현)
 * - PriorityQueue에서 거리가 가까운 것부터 꺼내기 위함
 */
package org.dfpl.dbp.rtree;

public class Entry implements Comparable<Entry> {
    private Object data;        // Point 또는 RTreeNode
    private double distance;    // source로부터의 거리

    public Entry(Object data, double distance) {
        // TODO: 구현 필요
    }

    /**
     * 거리를 기준으로 오름차순 정렬
     * - 거리가 짧을수록 우선순위가 높음
     */
    @Override
    public int compareTo(Entry other) {
        // TODO: 구현 필요
        return 0;
    }

    /**
     * Point인지 확인
     */
    public boolean isPoint() {
        // TODO: 구현 필요
        return false;
    }

    /**
     * RTreeNode인지 확인
     */
    public boolean isNode() {
        // TODO: 구현 필요
        return false;
    }

    // Getters
    public Object getData() { return data; }
    public double getDistance() { return distance; }
}
```

### 구현 위치: `RTreeImpl.java`

```java
/**
 * KNN 검색 (K-Nearest Neighbor)
 *
 * 구현 내용:
 * - source Point로부터 가장 가까운 k개의 Point를 찾아서 반환
 * - Best-First Search 알고리즘 사용 (우선순위 큐 기반)
 * - MINDIST(사각형-점 최소거리) 계산하여 효율적으로 탐색
 *
 * 필요한 작업:
 * 1. 결과를 저장할 List<Point> 생성
 * 2. PriorityQueue<Entry> 생성 (거리 기준 정렬)
 * 3. 루트를 Entry로 만들어서 PQ에 추가 (거리는 minDistance 사용)
 * 4. PQ에서 하나씩 꺼내며:
 *    - Point면 → 결과에 추가, k개 모이면 종료
 *    - LeafNode면 → 모든 Point를 Entry로 만들어 PQ에 추가
 *    - InternalNode면 → 모든 자식을 Entry로 만들어 PQ에 추가
 * 5. 결과 리스트의 Iterator 반환
 *
 * @param source 검색 기준점
 * @param maxCount k (찾을 개수)
 * @return 가까운 순서대로 정렬된 Point들
 */
@Override
public Iterator<Point> nearest(Point source, int maxCount) {
    // TODO: 구현 필요
    return null;
}
```

### GUI 시각화: `RTreePanel.java`

```java
/**
 * paintComponent에 추가할 KNN 시각화 요소:
 *
 * 1. Source Point 표시
 *    - 파란색 큰 원으로 표시
 *    - 라벨 "Source" 추가
 *
 * 2. KNN 결과 Point 표시
 *    - 빨간색 큰 원으로 표시
 *    - 각 Point에 순서 번호 표시 (1, 2, 3, ...)
 *    - Source부터 각 Point까지 선으로 연결
 *    - 거리 텍스트 표시
 *
 * 3. 탐색 과정 단계별 표시 (선택사항)
 *    - 현재 탐색 중인 MBR 노란색으로 강조
 *    - 로그 영역에 탐색 순서 출력
 */
```

### 예상 결과
- Source: `(75, 85)`
- K: `5`
- 결과: 5개 Point (거리순 정렬)
  ```
  Point [x=70.0, y=80.0] : 7.07
  Point [x=95.0, y=90.0] : 20.62
  Point [x=80.0, y=110.0] : 25.50
  Point [x=100.0, y=65.0] : 32.02
  Point [x=120.0, y=100.0] : 47.43
  ```

---

## 🎯 Task 4: 삭제 연산 (5점)

### 구현 위치: `RTreeImpl.java`

```java
/**
 * Point 삭제
 *
 * 구현 내용:
 * - 트리에서 지정된 Point를 찾아서 삭제
 * - 삭제 후 언더플로우(자식 수 < MIN_ENTRIES) 발생 시 재구성
 * - CondenseTree 알고리즘으로 트리 균형 유지
 *
 * 필요한 작업:
 * 1. FindLeaf로 Point가 있는 리프 노드 찾기
 * 2. 리프에서 Point 제거
 * 3. CondenseTree 호출하여 트리 재구성
 * 4. 제거된 노드의 엔트리들을 재삽입
 * 5. 루트가 비었거나 자식이 1개면 높이 감소
 * 6. size 감소
 * 7. GUI 업데이트
 *
 * @param point 삭제할 Point
 */
@Override
public void delete(Point point) {
    // TODO: 구현 필요
}

/**
 * Point가 있는 리프 노드 찾기
 *
 * 구현 내용:
 * - 트리를 탐색하여 지정된 Point를 포함하는 리프 노드 반환
 * - MBR에 Point가 포함되지 않으면 가지치기
 *
 * 필요한 작업:
 * 1. 리프 노드면 Point 존재 여부 확인 후 반환
 * 2. 내부 노드면 MBR이 Point를 포함하는 자식들을 재귀 탐색
 * 3. 찾으면 해당 리프 반환, 못 찾으면 null 반환
 *
 * @param node 현재 탐색 중인 노드
 * @param point 찾을 Point
 * @return Point를 포함하는 리프 노드 (없으면 null)
 */
private LeafNode findLeaf(RTreeNode node, Point point) {
    // TODO: 구현 필요
    return null;
}

/**
 * 트리 재구성 (Condense Tree)
 *
 * 구현 내용:
 * - 리프부터 루트까지 올라가며 MBR 갱신
 * - 언더플로우 노드 제거 및 엔트리 저장 (재삽입용)
 * - 트리의 균형 유지
 *
 * 필요한 작업:
 * 1. 제거된 엔트리들을 저장할 List 생성
 * 2. 현재 노드부터 루트까지 반복:
 *    - 언더플로우(자식 < MIN_ENTRIES)면 노드 제거, 엔트리 저장
 *    - 아니면 MBR 재계산
 * 3. 저장된 엔트리들을 트리에 재삽입
 * 4. 루트가 비었으면 null로 설정
 * 5. 루트의 자식이 1개면 그 자식을 새 루트로 (높이 감소)
 *
 * @param leaf 삭제가 발생한 리프 노드
 * @return 제거된 엔트리들의 리스트
 */
private List<Object> condenseTree(LeafNode leaf) {
    // TODO: 구현 필요
    return null;
}

/**
 * 제거된 엔트리들을 트리에 재삽입
 *
 * 구현 내용:
 * - CondenseTree에서 제거된 엔트리들을 다시 트리에 삽입
 * - Point면 add() 호출
 * - Node면 적절한 레벨에 삽입
 *
 * @param entries 재삽입할 엔트리들 (Point 또는 RTreeNode)
 */
private void reinsertEntries(List<Object> entries) {
    // TODO: 구현 필요
}
```

### GUI 시각화: `RTreePanel.java`

```java
/**
 * paintComponent에 추가할 삭제 시각화 요소:
 *
 * 1. 삭제할 Point 강조
 *    - 빨간 X 표시
 *    - 잠시 대기하여 사용자가 볼 수 있게
 *
 * 2. 언더플로우 노드 표시
 *    - 빨간색 점선으로 표시
 *    - 라벨 "Underflow" 추가
 *
 * 3. 재삽입 엔트리 표시 (선택사항)
 *    - 노란색으로 강조
 *
 * 4. 갱신된 MBR 애니메이션
 *    - MBR이 줄어드는 과정을 단계적으로 표시
 */
```

### 예상 결과
- Assignment45에서 30개 Point를 모두 삭제
- 마지막에 `rTree.isEmpty()` → `true` 반환

---

## 📊 발표 준비 (10점)

### 1. R-Tree 설명 발표 (5점)

- **핵심 코드 설명**
  - add() 메서드: ChooseLeaf → Split → AdjustTree 흐름
  - search() 메서드: 가지치기(Pruning) 동작 원리
  - nearest() 메서드: Best-First Search 알고리즘
  - delete() 메서드: CondenseTree와 재삽입 로직

- **시각화 설명**
  - GUI 구조 (Swing 사용)
  - 레벨별 색상 구분
  - 각 Task별 시각화 특징

### 2. 성능 평가 (5점)

#### 구현 필요: `PerformanceTest.java`

```java
/**
 * 성능 평가 클래스
 *
 * 구현 내용:
 * - R-Tree 사용 vs Brute Force(전체 탐색) 성능 비교
 * - Task 2 또는 Task 3 중 하나 선택
 */
package org.dfpl.dbp.rtree;

public class PerformanceTest {

    /**
     * Task 2 성능 비교: 범위 검색
     *
     * 측정 내용:
     * 1. R-Tree search(): 가지치기 활용
     * 2. Brute Force: 모든 Point를 순회하며 확인
     *
     * 비교 지표:
     * - 실행 시간 (ms)
     * - 방문한 노드 수
     * - 비교 연산 횟수
     */
    public static void compareRangeSearch() {
        // TODO: 구현 필요
        // 1. 많은 Point 삽입 (1000~10000개)
        // 2. R-Tree search() 시간 측정
        // 3. Brute Force 시간 측정
        // 4. 결과를 표로 정리
    }

    /**
     * Task 3 성능 비교: KNN 검색
     *
     * 측정 내용:
     * 1. R-Tree nearest(): Best-First Search
     * 2. Brute Force: 모든 Point 거리 계산 후 정렬
     *
     * 비교 지표:
     * - 실행 시간 (ms)
     * - 거리 계산 횟수
     */
    public static void compareKNNSearch() {
        // TODO: 구현 필요
    }

    /**
     * 결과를 표 형태로 출력
     */
    private static void printResults() {
        // TODO: 구현 필요
        // 예시:
        // | Point 수 | R-Tree (ms) | Brute Force (ms) | 성능 향상 |
        // |---------|-------------|------------------|----------|
        // | 1000    | 5           | 45               | 9배      |
        // | 5000    | 12          | 230              | 19배     |
        // | 10000   | 25          | 950              | 38배     |
    }
}
```

---

## 🔧 시각화를 위한 정보 저장

### GUI 팀원이 알아야 할 정보

각 알고리즘 구현 시 다음 정보를 Set에 저장해야 합니다:

#### Task 2: 범위 검색
```java
// RTreeImpl에 멤버 변수 추가
private Set<RTreeNode> lastVisitedNodes = new HashSet<>();
private Set<RTreeNode> lastPrunedNodes = new HashSet<>();
private Rectangle lastSearchArea = null;

// search() 메서드에서 설정
lastVisitedNodes = visitedNodes;
lastPrunedNodes = prunedNodes;
lastSearchArea = rectangle;

// GUI에서 가져가기
public Set<RTreeNode> getLastVisitedNodes() { return lastVisitedNodes; }
public Set<RTreeNode> getLastPrunedNodes() { return lastPrunedNodes; }
public Rectangle getLastSearchArea() { return lastSearchArea; }
```

#### Task 3: KNN 검색
```java
// RTreeImpl에 멤버 변수 추가
private Point lastKNNSource = null;
private List<Point> lastKNNResults = new ArrayList<>();

// nearest() 메서드에서 설정
lastKNNSource = source;
lastKNNResults = results;

// GUI에서 가져가기
public Point getLastKNNSource() { return lastKNNSource; }
public List<Point> getLastKNNResults() { return lastKNNResults; }
```

#### Task 4: 삭제
```java
// RTreeImpl에 멤버 변수 추가
private Point lastDeletedPoint = null;
private List<RTreeNode> lastUnderflowNodes = new ArrayList<>();

// delete() 메서드에서 설정
lastDeletedPoint = point;
// condenseTree에서 언더플로우 노드를 lastUnderflowNodes에 추가

// GUI에서 가져가기
public Point getLastDeletedPoint() { return lastDeletedPoint; }
public List<RTreeNode> getLastUnderflowNodes() { return lastUnderflowNodes; }
```

---

## ✅ 최종 체크리스트

### 구현 완료 기준

#### Task 1 (검증)
- [ ] Assignment45 실행 시 30개 Point 모두 삽입됨
- [ ] GUI에서 각 단계마다 MBR이 올바르게 표시됨
- [ ] 중복 Point가 추가되지 않음

#### Task 2
- [ ] search() 메서드 구현 완료
- [ ] 11개 Point가 정확히 반환됨
- [ ] GUI에서 검색 범위가 표시됨
- [ ] 가지치기된 영역이 시각적으로 구분됨

#### Task 3
- [ ] Entry 클래스 구현 완료
- [ ] nearest() 메서드 구현 완료
- [ ] 5개 Point가 거리순으로 정확히 반환됨
- [ ] GUI에서 Source와 결과가 표시됨
- [ ] 거리가 올바르게 계산됨

#### Task 4
- [ ] delete() 메서드 구현 완료
- [ ] findLeaf() 구현 완료
- [ ] condenseTree() 구현 완료
- [ ] 30개 Point 삭제 후 isEmpty() == true
- [ ] GUI에서 삭제 과정이 표시됨

#### 발표 준비
- [ ] PPT 작성 (설계, 코드 설명)
- [ ] 성능 평가 코드 작성
- [ ] 성능 비교 결과 표 작성
- [ ] 데모 시나리오 준비

---

## 📞 구현 시 주의사항

### 공통 규칙
1. **4-way 제약 준수**: MAX_ENTRIES = 4, MIN_ENTRIES = 2
2. **MBR 갱신**: 삽입/삭제 시 반드시 조상 노드들의 MBR 갱신
3. **null 체크**: 모든 메서드에서 null 확인
4. **예외 처리**: 빈 트리, 존재하지 않는 Point 등 처리
5. **GUI 동기화**: 트리 변경 시 시각화 정보 업데이트


# 브랜치 전략
main - 안정 버전
task2 - 범위 검색 구현
task3 - KNN 검색 구현
task4 - 삭제 연산 구현
gui - 시각화 구현


