package javautil.concurrent.atomic;

import java.util.concurrent.atomic.AtomicLong;

public class LongSetTest {
    private static final AtomicLong a = new AtomicLong();
    private static final AtomicLong b = new AtomicLong();
    public static void main(String[] args) {
    	long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100000000; i++) {
            a.set(i);
        }
        System.out.println(System.currentTimeMillis() - startTime);
        
        
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 100000000; i++) {
            b.lazySet(i);
        }
        System.out.println(System.currentTimeMillis() - startTime);
    }
}
