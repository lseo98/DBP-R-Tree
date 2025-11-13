package org.dfpl.dbp.rtree;

/**
 * R-Tree 노드의 추상 클래스
 * LeafNode와 InternalNode의 부모 클래스
 */
public abstract class RTreeNode {

	protected Rectangle mbr; // Minimum Bounding Rectangle
	protected RTreeNode parent; // 부모 노드
	protected boolean isLeaf; // 리프 노드 여부

	// 4-way R-Tree 제약
	protected static final int MAX_ENTRIES = 4;
	protected static final int MIN_ENTRIES = 2;

	public RTreeNode(boolean isLeaf) {
		this.isLeaf = isLeaf;
		this.parent = null;
		this.mbr = null;
	}

	/**
	 * 자식/엔트리 개수 반환
	 */
	public abstract int getChildCount();

	/**
	 * MBR 계산
	 */
	public abstract Rectangle calculateMBR();

	/**
	 * 오버플로우 체크 (자식 수 > MAX_ENTRIES)
	 */
	public abstract boolean isOverflow();

	/**
	 * 언더플로우 체크 (자식 수 < MIN_ENTRIES)
	 */
	public abstract boolean isUnderflow();

	// Getters and Setters
	public Rectangle getMbr() {
		return mbr;
	}

	public void setMbr(Rectangle mbr) {
		this.mbr = mbr;
	}

	public RTreeNode getParent() {
		return parent;
	}

	public void setParent(RTreeNode parent) {
		this.parent = parent;
	}

	public boolean isLeaf() {
		return isLeaf;
	}

	public boolean isRoot() {
		return parent == null;
	}
}
