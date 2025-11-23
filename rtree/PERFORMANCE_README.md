# R-Tree 성능 비교 가이드

## 개요

이 프로젝트는 R-Tree와 선형 스캔의 성능을 비교하는 도구를 제공합니다.
**중요**: 기존 `Assignment45.java`는 수정되지 않았으며, 모든 성능 비교 기능은 `PerformanceComparison.java`에 별도로 구현되어 있습니다.

## 파일 구조

```
rtree/src/main/java/org/dfpl/dbp/rtree/team1/
├── Assignment45.java             # 기존 과제 코드 (수정 안 됨)
├── RTreeImpl.java                # R-Tree 구현 (GUI 제어 생성자 추가됨)
├── PerformanceComparison.java    # 성능 비교 유틸리티 클래스 (직접 실행 불가) ⚠️
├── PerformanceTableMode.java     # 표 모드 실행 클래스 (Ctrl+F11) ✅
├── PerformanceDemoMode.java      # 데모 모드 실행 클래스 (Ctrl+F11) ✅
├── Point.java
├── Rectangle.java
└── RTree.java
```

**중요**: `PerformanceComparison.java`는 직접 실행하지 않습니다!

- 실행 시 안내 메시지만 표시됩니다.
- 실제 기능은 TableMode/DemoMode에서 사용합니다.

## 빌드 방법

```bash
cd rtree
mvn clean compile
```

## 실행 방법

### 1. 기존 과제 실행 (GUI 포함)

**IDE에서 (가장 간편):**

- `Assignment45.java` 열고 **Ctrl+F11** 또는 Run 버튼 클릭

**커맨드 라인:**

```bash
mvn exec:java -Dexec.mainClass="org.dfpl.dbp.rtree.team1.Assignment45"
```

또는

```bash
cd rtree
javac -d target/classes src/main/java/org/dfpl/dbp/rtree/team1/*.java
java -cp target/classes org.dfpl.dbp.rtree.team1.Assignment45
```

### 2. 성능 비교 - 표 모드 (기본)

다양한 데이터 크기(100, 500, 1000, 5000, 10000)에 대해 성능을 비교하고 표로 출력합니다.

**IDE에서 (가장 간편):**

- `PerformanceTableMode.java` 열고 **Ctrl+F11** 또는 Run 버튼 클릭

**커맨드 라인:**

```bash
mvn exec:java -Dexec.mainClass="org.dfpl.dbp.rtree.team1.PerformanceTableMode"
```

또는

```bash
java -cp target/classes org.dfpl.dbp.rtree.team1.PerformanceTableMode
```

**출력 예시:**

```
====================================================================================================
R-Tree vs 선형 스캔 성능 비교 (표 모드)
====================================================================================================

데이터 크기 | Range RTree(ms) | Range Linear(ms) | Speedup    | kNN RTree(ms)   | kNN Linear(ms)  | Speedup
----------------------------------------------------------------------------------------------------
100        |           0.123 |           0.089 |      0.72x |           0.156 |           0.234 |      1.50x
500        |           0.234 |           0.456 |      1.95x |           0.267 |           1.123 |      4.21x
1000       |           0.345 |           0.912 |      2.64x |           0.312 |           2.234 |      7.16x
5000       |           0.567 |           4.567 |      8.05x |           0.456 |          11.234 |     24.64x
10000      |           0.789 |           9.123 |     11.56x |           0.523 |          22.456 |     42.93x
====================================================================================================
```

### 3. 성능 비교 - 데모 모드

1000개의 포인트로 단일 쿼리를 실행하고 결과를 상세히 출력합니다.

**IDE에서 (가장 간편):**

- `PerformanceDemoMode.java` 열고 **Ctrl+F11** 또는 Run 버튼 클릭

**커맨드 라인:**

```bash
mvn exec:java -Dexec.mainClass="org.dfpl.dbp.rtree.team1.PerformanceDemoMode"
```

또는

```bash
java -cp target/classes org.dfpl.dbp.rtree.team1.PerformanceDemoMode
```

**출력 예시:**

```
================================================================================
R-Tree vs 선형 스캔 성능 비교 (데모 모드)
================================================================================

데이터 생성 중... (크기: 1000)
R-Tree 구축 완료!

--- 범위 검색 테스트 ---
검색 범위: Rectangle [leftTop=(200.0,200.0), rightBottom=(400.0,400.0)]
R-Tree 결과: 45개 점, 소요 시간: 0.312 ms
선형 스캔 결과: 45개 점, 소요 시간: 0.867 ms
속도 향상: 2.78x

--- k-NN 검색 테스트 ---
쿼리 포인트: Point [x=500.0, y=500.0], k = 10
R-Tree 결과: 10개 점, 소요 시간: 0.289 ms
선형 스캔 결과: 10개 점, 소요 시간: 2.145 ms
속도 향상: 7.42x

k-NN 결과 샘플 (상위 5개):
  1. Point [x=502.3, y=498.7] (거리: 3.12)
  2. Point [x=497.1, y=504.2] (거리: 4.89)
  3. Point [x=495.6, y=493.8] (거리: 7.23)
  4. Point [x=508.2, y=495.4] (거리: 9.45)
  5. Point [x=491.3, y=507.9] (거리: 11.67)

================================================================================
데모 완료!
```

## 주요 기능

### 1. 재현성 보장

- 랜덤 시드(SEED = 42)를 고정하여 매번 동일한 결과 생성
- 동일한 환경에서 동일한 성능 측정 가능

### 2. JVM 워밍업

- 각 벤치마크 전에 5회의 워밍업 실행
- JIT 컴파일러 최적화 효과를 반영한 정확한 측정

### 3. 반복 측정

- 각 쿼리를 10회 반복 실행하여 평균 계산
- 측정 오차 최소화

### 4. GUI 비활성화

- `new RTreeImpl(false)`로 시각화 없이 순수 성능 측정
- Thread.sleep 등의 지연 없음

### 5. 두 가지 쿼리 유형

- **범위 검색**: 특정 사각형 영역 내의 모든 포인트 검색
- **k-NN 검색**: 쿼리 포인트에서 가장 가까운 k개의 포인트 검색

## 구현 세부사항

### R-Tree 구현

- `RTreeImpl.addFast()`: GUI 없이 포인트 추가
- `RTreeImpl.searchFast()`: GUI 없이 범위 검색
- `RTreeImpl.nearestFast()`: GUI 없이 k-NN 검색

### 선형 스캔 구현

- `linearRangeSearch()`: 모든 포인트를 순회하여 범위 확인
- `linearKnn()`: 우선순위 큐를 사용한 k-NN 검색

## 성능 특성

### 데이터 크기별 예상 성능

- **소규모 (< 500)**: 선형 스캔이 오히려 빠를 수 있음 (오버헤드)
- **중규모 (500-5000)**: R-Tree가 2~10배 빠름
- **대규모 (> 5000)**: R-Tree가 10배 이상 빠름

### 쿼리 유형별 특성

- **범위 검색**: 검색 영역이 작을수록 R-Tree 유리
- **k-NN**: k가 작고 데이터가 많을수록 R-Tree 유리

## 테스트 환경

- **Java 버전**: JDK 8 이상
- **Maven**: 3.6 이상
- **데이터 범위**: 0 ~ 1000 (x, y 좌표)
- **쿼리 범위**: 250 ~ 750 (중간 영역)
- **k 값**: 10
- **워밍업**: 10회 (JVM 최적화)
- **측정 반복**: 50회 (중앙값 사용)

## 주의사항

1. **기존 코드 보존**: `Assignment45.java`는 전혀 수정되지 않았습니다.
2. **GUI 분리**: 성능 비교는 GUI 없이 실행됩니다.
3. **외부 라이브러리**: 표준 라이브러리만 사용합니다 (java.util.\* 등).
4. **멀티스레드**: 단일 스레드에서 순차적으로 실행됩니다.

## 문제 해결

### Q: 컴파일 에러가 발생합니다.

A: Maven을 사용하여 빌드하세요: `mvn clean compile`

### Q: 성능 결과가 이상합니다.

A: 다음을 확인하세요:

- 다른 프로그램이 CPU를 많이 사용하고 있지 않은지
- JVM 힙 메모리가 충분한지 (`-Xmx512m` 등)
- 첫 실행 시 JIT 워밍업으로 인해 느릴 수 있음

### Q: Assignment45 실행 시 GUI가 안 뜹니다.

A: X11/Display 환경을 확인하세요. 성능 비교는 GUI 없이 실행 가능합니다.

## 참고

- R-Tree 알고리즘: Quadratic Split
- 최대 엔트리: 4개 (MAX_ENTRIES)
- 최소 엔트리: 2개 (MIN_ENTRIES)
