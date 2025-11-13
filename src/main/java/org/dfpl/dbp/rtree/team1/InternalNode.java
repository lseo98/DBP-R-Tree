package org.dfpl.dbp.rtree.team1;

import java.util.ArrayList;
import java.util.List;

/**
 * R-Tree의 내부 노드
 * 자식 노드들을 저장
 */
public class InternalNode extends RTreeNode {

	private List<RTreeNode> children; // 자식 노드들

	public InternalNode() {
		super(false); // isLeaf = false
		this.children = new ArrayList<>();
	}

	/**
	 * 자식 노드 추가
	 */
	public void addChild(RTreeNode child) {
		children.add(child);
		child.setParent(this);
	}

	/**
	 * 자식 노드 제거
	 */
	public void removeChild(RTreeNode child) {
		children.remove(child);
		if (child.getParent() == this) {
			child.setParent(null);
		}
	}

	public List<RTreeNode> getChildren() {
		return children;
	}

	@Override
	public int getChildCount() {
		return children.size();
	}

	@Override
	public Rectangle calculateMBR() {
		if (children.isEmpty()) {
			return null;
		}

		Rectangle result = children.get(0).getMbr();
		for (int i = 1; i < children.size(); i++) {
			result = result.merge(children.get(i).getMbr());
		}
		return result;
	}

	@Override
	public boolean isOverflow() {
		return children.size() > MAX_ENTRIES;
	}

	@Override
	public boolean isUnderflow() {
		return children.size() < MIN_ENTRIES;
	}

	@Override
	public String toString() {
		return "InternalNode [children=" + children.size() + ", mbr=" + mbr + "]";
	}
}
