package org.dfpl.dbp.rtree.team1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * RTreeImpl: 4-way R-Tree의 구현 클래스
 *
 * R-Tree란?
 * - 공간 데이터를 효율적으로 저장하고 검색하기 위한 트리 자료구조
 * - B-Tree의 공간 버전 (다차원 데이터용)
 * - 각 노드는 MBR (Minimum Bounding Rectangle)을 가짐
 *
 * 4-way R-Tree란?
 * - 각 노드가 최대 4개의 자식을 가질 수 있음
 * - 리프 노드는 최대 4개의 Point를 저장
 * - 최소 2개 이상 유지 (루트 제외)
 *
 * 주요 연산:
 * 1. add(point): Point 삽입
 * 2. search(rectangle): 범위 검색
 * 3. nearest(point, k): KNN 검색
 * 4. delete(point): Point 삭제
 */
public class RTreeImpl implements RTree {

	// 요건 4-way R-Tree로 구현한다.
	// Maven Project로 만든다.
	// 기존의 R-Tree를 활용하지 않는다.
	// 여러분의 프로젝트에는 최소한의 dependency가 포함되어 있어야 함.
	// 멤버 변수의 활용은 어느정도 자유로움
	// 단, R-Tree 구현이어야 하고, 요행을 바라지 않는다.

	// ===== 멤버 변수 =====
	private RTreeNode root; // 트리의 루트 노드 (트리 탐색의 시작점)
	private int size; // 트리에 저장된 Point의 총 개수
	private int height; // 트리의 높이 (루트=0, 리프까지의 레벨 수)
	private RTreeVisualizer visualizer; // GUI 시각화 객체 (null이면 GUI 비활성화)

	// ===== 4-way R-Tree 제약 조건 =====
	// static final: 변경 불가능한 상수 (클래스 레벨에서 공유)
	private static final int MAX_ENTRIES = 4; // 노드당 최대 엔트리 수
	private static final int MIN_ENTRIES = 2; // 노드당 최소 엔트리 수 (루트 제외)

	public RTreeImpl() {
		this.root = null;
		this.size = 0;
		this.height = 0;
		this.visualizer = null;
	}

	/**
	 * GUI 시각화 활성화
	 */
	public void enableVisualization() {
		if (visualizer == null) {
			visualizer = new RTreeVisualizer(this);
			visualizer.setVisible(true);
		}
	}

	/**
	 * add: Point를 R-Tree에 삽입
	 *
	 * ★★★ R-Tree 삽입 알고리즘 ★★★
	 *
	 * 전체 흐름:
	 * 1. 중복 체크 → 이미 있으면 추가하지 않음
	 * 2. 빈 트리 처리 → 첫 Point면 루트 리프 생성
	 * 3. ChooseLeaf → 면적 증가가 최소인 리프 선택
	 * 4. 리프에 추가 → Point를 선택된 리프에 삽입
	 * 5. 오버플로우 체크 → 5개 이상이면 Split
	 * 6. AdjustTree → 리프부터 루트까지 MBR 갱신
	 * 7. GUI 업데이트 → 시각화 (있으면)
	 *
	 * @param point 삽입할 점
	 */
	@Override
	public void add(Point point) {
		// ===== 1단계: 중복 체크 =====
		// contains(): 트리에 이미 같은 좌표의 점이 있는지 확인
		if (contains(point)) {
			System.out.println("Point already exists: " + point);
			return; // 중복이면 추가하지 않고 종료
		}

		// ===== 2단계: 빈 트리 처리 (첫 삽입) =====
		if (root == null) {
			// 새로운 리프 노드를 루트로 생성
			root = new LeafNode();

			// 형변환 필요: root는 RTreeNode 타입이지만
			// addPoint()는 LeafNode에만 있으므로 캐스팅
			// (LeafNode)는 "root를 LeafNode로 취급하라"는 의미
			((LeafNode) root).addPoint(point);

			// MBR 계산 및 설정
			root.setMbr(root.calculateMBR());

			// 크기 증가
			size++;

			System.out.println("Added first point: " + point);
			return; // 첫 삽입은 여기서 종료
		}

		// ===== 3단계: ChooseLeaf - 삽입할 리프 선택 =====
		// 면적 증가가 최소인 경로를 따라 리프까지 내려감
		LeafNode leaf = chooseLeaf(root, point);

		// ===== 4단계: 리프에 Point 추가 =====
		leaf.addPoint(point);
		size++; // 전체 크기 증가

		// ===== 5단계: 오버플로우 체크 및 처리 =====
		if (leaf.isOverflow()) {
			// 리프에 5개 이상 → 분할 필요
			// splitNode()는 leaf를 2개 그룹으로 나누고 새 노드 반환
			RTreeNode newNode = splitNode(leaf);

			// adjustTree()에 두 노드 모두 전달
			// leaf: 기존 노드 (일부 엔트리 유지)
			// newNode: 새로 생성된 노드 (나머지 엔트리)
			adjustTree(leaf, newNode);
		} else {
			// 오버플로우 없음 → MBR만 갱신하면 됨
			// adjustTree(leaf, null)은 newNode가 없다는 의미
			adjustTree(leaf, null);
		}

		System.out.println("Added point: " + point);

		// ===== 6단계: GUI 업데이트 =====
		// visualizer가 있으면 (null이 아니면) 화면 갱신
		updateVisualization("Added point: " + point, point);
	}

	/**
	 * GUI 시각화 업데이트
	 */
	private void updateVisualization(String message, Point highlightPoint) {
		if (visualizer != null) {
			visualizer.showStep(message);
			if (highlightPoint != null) {
				visualizer.highlightPoint(highlightPoint);
			}
			visualizer.updateTree();
			visualizer.sleep(300); // 애니메이션 효과
		}
	}

	/**
	 * Point가 트리에 이미 존재하는지 확인
	 */
	private boolean contains(Point point) {
		if (root == null)
			return false;
		return containsRecursive(root, point);
	}

	private boolean containsRecursive(RTreeNode node, Point point) {
		if (node.isLeaf()) {
			LeafNode leaf = (LeafNode) node;
			return leaf.containsPoint(point);
		}

		InternalNode internal = (InternalNode) node;
		for (RTreeNode child : internal.getChildren()) {
			if (child.getMbr().contains(point)) {
				if (containsRecursive(child, point)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * chooseLeaf: 삽입할 리프 노드 선택 (재귀 알고리즘)
	 *
	 * ★★★ R-Tree의 핵심 알고리즘 ★★★
	 * 목표: 면적 증가가 최소인 리프를 찾아 그곳에 삽입
	 *
	 * 왜 면적 증가를 최소화?
	 * - MBR이 작을수록 불필요한 영역을 적게 포함
	 * - 검색 시 가지치기 효율이 높아짐
	 * - 트리의 검색 성능 향상
	 *
	 * 알고리즘 흐름:
	 * 1. 현재 노드가 리프면 → 여기가 삽입 위치!
	 * 2. 내부 노드면 → 모든 자식 검사
	 *    - 각 자식의 면적 증가량 계산
	 *    - 증가량이 최소인 자식 선택
	 *    - 동점이면 면적이 작은 자식 선택
	 * 3. 선택된 자식으로 재귀 호출
	 *
	 * @param node 현재 탐색 중인 노드
	 * @param point 삽입할 점
	 * @return 최종 선택된 리프 노드
	 *
	 * 예시:
	 * 루트(내부 노드)
	 *   ├─ Child A: 면적 증가 +10 ← 선택!
	 *   ├─ Child B: 면적 증가 +25
	 *   └─ Child C: 면적 증가 +15
	 * → Child A로 내려가서 재귀 호출
	 */
	private LeafNode chooseLeaf(RTreeNode node, Point point) {
		// ===== 종료 조건: 리프 노드 도달 =====
		// isLeaf(): node가 리프인지 확인 (LeafNode의 플래그)
		if (node.isLeaf()) {
			// 형변환: RTreeNode → LeafNode
			return (LeafNode) node;
		}

		// ===== 재귀 단계: 내부 노드 처리 =====
		// 형변환: RTreeNode → InternalNode
		InternalNode internal = (InternalNode) node;

		// 최선의 자식을 찾기 위한 변수들
		RTreeNode bestChild = null; // 선택될 자식 노드
		double minEnlargement = Double.MAX_VALUE; // 최소 면적 증가량 (매우 큰 값으로 초기화)
		double minArea = Double.MAX_VALUE; // 동점 시 비교용 최소 면적

		// ===== 모든 자식 노드 순회 =====
		// for-each 문법: internal의 모든 자식을 하나씩 검사
		for (RTreeNode child : internal.getChildren()) {
			// 이 자식에 point를 추가하면 MBR이 얼마나 증가하는지 계산
			double enlargement = child.getMbr().enlargement(point);

			// ===== 케이스 1: 더 작은 증가량 발견 =====
			if (enlargement < minEnlargement) {
				// 기록 갱신
				minEnlargement = enlargement;
				minArea = child.getMbr().area(); // 동점 대비 면적 저장
				bestChild = child; // 이 자식이 현재 최선
			}
			// ===== 케이스 2: 동점 (같은 증가량) =====
			// Tie-breaking: 면적이 작은 쪽 선택
			else if (enlargement == minEnlargement) {
				double area = child.getMbr().area();
				if (area < minArea) {
					// 면적이 더 작으면 이 자식 선택
					minArea = area;
					bestChild = child;
				}
			}
			// else: 증가량이 더 크면 무시
		}

		// ===== 재귀 호출 =====
		// 선택된 자식으로 한 단계 더 내려가서 계속 탐색
		return chooseLeaf(bestChild, point);
	}

	/**
	 * SplitNode: 노드 분할 (오버플로우 처리)
	 */
	private RTreeNode splitNode(RTreeNode node) {
		if (node.isLeaf()) {
			return splitLeafNode((LeafNode) node);
		} else {
			return splitInternalNode((InternalNode) node);
		}
	}

	/**
	 * 리프 노드 분할 (LinearSplit)
	 */
	private LeafNode splitLeafNode(LeafNode node) {
		List<Point> allPoints = new ArrayList<>(node.getPoints());

		// 1. PickSeeds: 가장 먼 두 점 선택
		Point[] seeds = pickSeeds(allPoints);

		// 2. 두 그룹으로 분배
		List<Point> group1 = new ArrayList<>();
		List<Point> group2 = new ArrayList<>();
		group1.add(seeds[0]);
		group2.add(seeds[1]);
		allPoints.remove(seeds[0]);
		allPoints.remove(seeds[1]);

		// 3. 나머지 점들 분배
		distributePoints(allPoints, group1, group2);

		// 4. 새 노드 생성
		node.getPoints().clear();
		node.getPoints().addAll(group1);
		node.setMbr(node.calculateMBR());

		LeafNode newNode = new LeafNode();
		newNode.getPoints().addAll(group2);
		newNode.setMbr(newNode.calculateMBR());
		newNode.setParent(node.getParent());

		return newNode;
	}

	/**
	 * 내부 노드 분할
	 */
	private InternalNode splitInternalNode(InternalNode node) {
		List<RTreeNode> allChildren = new ArrayList<>(node.getChildren());

		// PickSeeds for nodes
		RTreeNode[] seeds = pickSeedsForNodes(allChildren);

		List<RTreeNode> group1 = new ArrayList<>();
		List<RTreeNode> group2 = new ArrayList<>();
		group1.add(seeds[0]);
		group2.add(seeds[1]);
		allChildren.remove(seeds[0]);
		allChildren.remove(seeds[1]);

		distributeNodes(allChildren, group1, group2);

		node.getChildren().clear();
		for (RTreeNode child : group1) {
			node.addChild(child);
		}
		node.setMbr(node.calculateMBR());

		InternalNode newNode = new InternalNode();
		for (RTreeNode child : group2) {
			newNode.addChild(child);
		}
		newNode.setMbr(newNode.calculateMBR());
		newNode.setParent(node.getParent());

		return newNode;
	}

	/**
	 * PickSeeds: X/Y축에서 가장 분리된 두 점 선택
	 */
	private Point[] pickSeeds(List<Point> points) {
		Point minX = points.stream().min(Comparator.comparing(Point::getX)).get();
		Point maxX = points.stream().max(Comparator.comparing(Point::getX)).get();
		double xSeparation = maxX.getX() - minX.getX();

		Point minY = points.stream().min(Comparator.comparing(Point::getY)).get();
		Point maxY = points.stream().max(Comparator.comparing(Point::getY)).get();
		double ySeparation = maxY.getY() - minY.getY();

		if (xSeparation > ySeparation) {
			return new Point[] { minX, maxX };
		} else {
			return new Point[] { minY, maxY };
		}
	}

	/**
	 * PickSeeds for nodes
	 */
	private RTreeNode[] pickSeedsForNodes(List<RTreeNode> nodes) {
		// 간단하게 첫 번째와 마지막 노드 선택
		// (더 정교한 알고리즘 사용 가능)
		return new RTreeNode[] { nodes.get(0), nodes.get(nodes.size() - 1) };
	}

	/**
	 * Point들을 두 그룹으로 분배
	 */
	private void distributePoints(List<Point> remaining, List<Point> group1, List<Point> group2) {
		while (!remaining.isEmpty()) {
			// 최소 개수 보장
			if (group1.size() + remaining.size() == MIN_ENTRIES) {
				group1.addAll(remaining);
				break;
			}
			if (group2.size() + remaining.size() == MIN_ENTRIES) {
				group2.addAll(remaining);
				break;
			}

			// 면적 증가가 작은 그룹에 추가
			Point next = remaining.get(0);
			Rectangle mbr1 = Rectangle.createMBR(group1);
			Rectangle mbr2 = Rectangle.createMBR(group2);

			double enlargement1 = mbr1.enlargement(next);
			double enlargement2 = mbr2.enlargement(next);

			if (enlargement1 < enlargement2) {
				group1.add(next);
			} else if (enlargement2 < enlargement1) {
				group2.add(next);
			} else {
				// 동점이면 면적이 작은 쪽에 추가
				if (mbr1.area() <= mbr2.area()) {
					group1.add(next);
				} else {
					group2.add(next);
				}
			}

			remaining.remove(next);
		}
	}

	/**
	 * 노드들을 두 그룹으로 분배
	 */
	private void distributeNodes(List<RTreeNode> remaining, List<RTreeNode> group1, List<RTreeNode> group2) {
		while (!remaining.isEmpty()) {
			if (group1.size() + remaining.size() == MIN_ENTRIES) {
				group1.addAll(remaining);
				break;
			}
			if (group2.size() + remaining.size() == MIN_ENTRIES) {
				group2.addAll(remaining);
				break;
			}

			RTreeNode next = remaining.get(0);
			Rectangle mbr1 = calculateGroupMBR(group1);
			Rectangle mbr2 = calculateGroupMBR(group2);

			double enlargement1 = mbr1.enlargement(next.getMbr().getLeftTop());
			double enlargement2 = mbr2.enlargement(next.getMbr().getLeftTop());

			if (enlargement1 < enlargement2) {
				group1.add(next);
			} else if (enlargement2 < enlargement1) {
				group2.add(next);
			} else {
				if (mbr1.area() <= mbr2.area()) {
					group1.add(next);
				} else {
					group2.add(next);
				}
			}

			remaining.remove(next);
		}
	}

	/**
	 * 노드 그룹의 MBR 계산
	 */
	private Rectangle calculateGroupMBR(List<RTreeNode> nodes) {
		Rectangle result = nodes.get(0).getMbr();
		for (int i = 1; i < nodes.size(); i++) {
			result = result.merge(nodes.get(i).getMbr());
		}
		return result;
	}

	/**
	 * AdjustTree: 리프부터 루트까지 MBR 갱신
	 */
	private void adjustTree(RTreeNode node, RTreeNode newNode) {
		while (!node.isRoot()) {
			RTreeNode parent = node.getParent();
			InternalNode internalParent = (InternalNode) parent;

			// MBR 갱신
			node.setMbr(node.calculateMBR());

			// 새 노드가 있으면 부모에 추가
			if (newNode != null) {
				internalParent.addChild(newNode);
				newNode.setParent(internalParent);

				// 부모가 오버플로우하면 분할
				if (internalParent.isOverflow()) {
					RTreeNode newParent = splitNode(internalParent);
					node = internalParent;
					newNode = newParent;
					continue;
				}
			}

			node = parent;
			newNode = null;
		}

		// 루트 분할 처리
		if (newNode != null) {
			InternalNode newRoot = new InternalNode();
			newRoot.addChild(root);
			newRoot.addChild(newNode);
			root.setParent(newRoot);
			newNode.setParent(newRoot);
			newRoot.setMbr(newRoot.calculateMBR());
			root = newRoot;
			height++;
		} else {
			root.setMbr(root.calculateMBR());
		}
	}

	@Override
	public Iterator<Point> search(Rectangle rectangle) {
		// TODO: Task 2에서 구현
		return null;
	}

	@Override
	public Iterator<Point> nearest(Point source, int maxCount) {
		// TODO: Task 3에서 구현
		return null;
	}

	@Override
	public void delete(Point point) {
		// TODO: Task 4에서 구현
	}

	@Override
	public boolean isEmpty() {
		return root == null || size == 0;
	}

	// Getters
	public RTreeNode getRoot() {
		return root;
	}

	public int getSize() {
		return size;
	}

	public int getHeight() {
		return height;
	}
}
