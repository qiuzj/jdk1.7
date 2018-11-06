package javautil.concurrent;

import java.util.concurrent.TimeUnit;

public class TimeUnitTest {

	public static void main(String[] args) {
		second();
	}
	
	public static void second() {
		System.out.println(TimeUnit.SECONDS.toSeconds(1));
		System.out.println(TimeUnit.SECONDS.toMillis(1));
		System.out.println(TimeUnit.SECONDS.toMicros(1));
		System.out.println(TimeUnit.SECONDS.toNanos(1));
		
		System.out.println(TimeUnit.SECONDS.toMinutes(60));
		System.out.println(TimeUnit.SECONDS.toHours(3600));
		System.out.println(TimeUnit.SECONDS.toDays(86400));
	}
	
}
