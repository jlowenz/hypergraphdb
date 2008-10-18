package org.hypergraphdb.query.impl;

import org.hypergraphdb.HGRandomAccessResult;
//import org.hypergraphdb.HGRandomAccessResult.GotoResult;
import org.hypergraphdb.HGSearchResult;
import org.hypergraphdb.util.ArrayBasedSet;

@SuppressWarnings("unchecked")
public class InMemoryIntersectionResult<T> implements HGRandomAccessResult<T>, RSCombiner<T>
{
	private HGRandomAccessResult<T> left, right;
	private HGRandomAccessResult<T> intersection = null;
	
	private void intersect()
	{
		if (intersection != null)
			return;
		ArrayBasedSet<Object> set = new ArrayBasedSet<Object>(new Object[0]);
		ZigZagIntersectionResult zigzag = new ZigZagIntersectionResult(left, right);
		while (zigzag.hasNext())
			set.add(zigzag.next());
		intersection = (HGRandomAccessResult<T>)set.getSearchResult();
	}
	
	public GotoResult goTo(T value, boolean exactMatch)
	{
		intersect();
		return intersection.goTo(value, exactMatch);
	}

	public void close()
	{
		if (intersection != null)
			intersection.close();
	}

	public T current()
	{
		intersect();
		return intersection.current();
	}

	public boolean isOrdered()
	{
		return true;
	}

	public boolean hasPrev()
	{
		intersect();
		return intersection.hasPrev();
	}

	public T prev()
	{
		intersect();
		return intersection.prev();
	}

	public boolean hasNext()
	{
		intersect();
		return intersection.hasNext();
	}

	public T next()
	{
		intersect();
		return intersection.next();
	}

	public void remove()
	{
		intersect();
		intersection.remove();
	}

	public void init(HGSearchResult<T> l, HGSearchResult<T> r)
	{
		this.left = (HGRandomAccessResult<T>)left;
		this.right = (HGRandomAccessResult<T>)right;
	}
}