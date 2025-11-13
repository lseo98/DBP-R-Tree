package org.dfpl.dbp.rtree;

import java.util.ArrayList;
import java.util.List;

/**
 * R-Tree의 리프 노드
 * 실제 Point 데이터를 저장
 */
public class LeafNode extends RTreeNode {

	private List<Point> points; // 저장된 Point들

	public LeafNode() {
		super(true); // isLeaf = true
		this.points = new ArrayList<>();
	}

	/**
	 * Point 추가
	 */
	public void addPoint(Point p) {
		points.add(p);
	}

	/**
	 * Point 제거
	 */
	public void removePoint(Point p) {
		points.remove(p);
	}

	/**
	 * Point 존재 여부 확인
	 */
	public boolean containsPoint(Point p) {
		for (Point point : points) {
			if (point.getX() == p.getX() && point.getY() == p.getY()) {
				return true;
			}
		}
		return false;
	}

	public List<Point> getPoints() {
		return points;
	}

	@Override
	public int getChildCount() {
		return points.size();
	}

	@Override
	public Rectangle calculateMBR() {
		if (points.isEmpty()) {
			return null;
		}
		return Rectangle.createMBR(points);
	}

	@Override
	public boolean isOverflow() {
		return points.size() > MAX_ENTRIES;
	}

	@Override
	public boolean isUnderflow() {
		return points.size() < MIN_ENTRIES;
	}

	@Override
	public String toString() {
		return "LeafNode [points=" + points.size() + ", mbr=" + mbr + "]";
	}
}
