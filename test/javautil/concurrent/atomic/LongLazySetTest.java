package javautil.concurrent.atomic;

import java.util.concurrent.atomic.AtomicLong;

public class LongLazySetTest {
    private static final AtomicLong a = new AtomicLong();
    public static void main(String[] args) {
    	long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100000000; i++) {
            a.lazySet(i);
        }
        System.out.println(System.currentTimeMillis() - startTime);
    }
}
