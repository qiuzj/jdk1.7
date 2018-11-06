package javautil.concurrent;

public class HashTest {

	public static void main(String[] args) {
		HashTest obj = new HashTest();
		obj.testConflict();
		System.out.println();
		obj.testNoConflict();
	}

	public void testConflict() {
		System.out.println(Integer.parseInt("0001111", 2) & 15);
		System.out.println(Integer.parseInt("0011111", 2) & 15);
		System.out.println(Integer.parseInt("0111111", 2) & 15);
		System.out.println(Integer.parseInt("1111111", 2) & 15);
	}

	public void testNoConflict() {
		System.out.println(rehash(hash(Integer.parseInt("0001111", 2))) & 15);
		System.out.println(rehash(hash(Integer.parseInt("0011111", 2))) & 15);
		System.out.println(rehash(hash(Integer.parseInt("0111111", 2))) & 15);
		System.out.println(rehash(hash(Integer.parseInt("1111111", 2))) & 15);
	}

	private int rehash(int hash) {
		int segmentShift = 28;
		int segmentMask = 15;
		return (hash >>> segmentShift) & segmentMask;
	}

	/**
	 * Wang/Jenkins hash的变种算法
	 * 
	 * @param k
	 * @return
	 */
	private int hash(int k) {
		int h = 0;
		h ^= k;

		// single-word Wang/Jenkins hash的变种算法
		// Spread bits to regularize both segment and index locations,
		// using variant of single-word Wang/Jenkins hash.
		h += (h << 15) ^ 0xffffcd7d;
		h ^= (h >>> 10);
		h += (h << 3);
		h ^= (h >>> 6);
		h += (h << 2) + (h << 14);
		return h ^ (h >>> 16);
	}

}
