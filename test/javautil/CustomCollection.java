package javautil;

import java.util.ArrayList;
import java.util.Iterator;

public class CustomCollection<T> implements Iterable<T> {

	private ArrayList<T> bucket;

	public CustomCollection() {
		bucket = new ArrayList<T>();
	}

	public int size() {
		return bucket.size();
	}

	public boolean isEmpty() {
		return bucket.isEmpty();
	}

	public boolean contains(T o) {
		return bucket.contains(o);
	}

	public boolean add(T e) {
		return bucket.add(e);
	}

	public boolean remove(T o) {
		return bucket.remove(o);
	}

	@Override
	public Iterator<T> iterator() {
		return bucket.iterator();
	}

}