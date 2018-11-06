package javautil.concurrent.atomic;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class ArrayTest {

	public static void main(String[] args) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		// 通过反射得到theUnsafe对应的Field对象
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        // 设置该Field为可访问
        field.setAccessible(true);
        // 通过Field得到该Field对应的具体对象，传入null是因为该Field为static的
        Unsafe unsafe = (Unsafe) field.get(null);
        
		int base = unsafe.arrayBaseOffset(long[].class);
	    int shift;
	    long[] array;

        int scale = unsafe.arrayIndexScale(long[].class);
        if ((scale & (scale - 1)) != 0)
            throw new Error("data type scale not a power of two");
        shift = 31 - Integer.numberOfLeadingZeros(scale);
        
        System.out.println(unsafe.arrayBaseOffset(byte[].class));
        System.out.println(unsafe.arrayBaseOffset(short[].class));
        System.out.println(unsafe.arrayBaseOffset(char[].class));
        System.out.println(unsafe.arrayBaseOffset(int[].class));
        System.out.println(unsafe.arrayBaseOffset(long[].class));
        System.out.println(unsafe.arrayBaseOffset(float[].class));
        System.out.println(unsafe.arrayBaseOffset(double[].class));
        System.out.println(unsafe.arrayBaseOffset(String[].class));
        System.out.println(unsafe.arrayBaseOffset(Object[].class));
        System.out.println(unsafe.arrayBaseOffset(Long[].class));

        System.out.println();
        System.out.println(unsafe.arrayIndexScale(byte[].class));
        System.out.println(unsafe.arrayIndexScale(short[].class));
        System.out.println(unsafe.arrayIndexScale(char[].class));
        System.out.println(unsafe.arrayIndexScale(int[].class));
        System.out.println(unsafe.arrayIndexScale(long[].class));
        System.out.println(unsafe.arrayIndexScale(float[].class));
        System.out.println(unsafe.arrayIndexScale(double[].class));
        System.out.println(unsafe.arrayIndexScale(String[].class));
        System.out.println(unsafe.arrayIndexScale(Object[].class));
        System.out.println(unsafe.arrayIndexScale(Integer[].class));
        
        System.out.println();
        System.out.println(base);
        System.out.println(scale);
        System.out.println(shift);
	}

}
