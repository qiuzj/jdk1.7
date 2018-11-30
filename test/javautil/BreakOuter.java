package javautil;

public class BreakOuter {

	public static void main(String[] args) {
		outer:
		for (;;) {
			for (int i = 0; i < 10; i++) {
				System.out.println(i);
				break outer;
//				continue outer;
			}
		}
	}

}
