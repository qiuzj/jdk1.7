

public class StringTest2 {

	public static void main(String[] args) {
		String s = new String("1");
	    s.intern();
	    String s2 = "1";
	    System.out.println(s == s2);

	    String s3 = new String("1") + new String("1");
	    s3.intern();
	    String s4 = "11";
	    System.out.println(s3 == s4);
	    
	    go();
	}

	private static void go() {
		String s = new String("1");
	    String s2 = "1";
	    s.intern();
	    System.out.println(s == s2);

	    String s3 = new String("1") + new String("1");
	    String s4 = "11";
	    s3.intern();
	    System.out.println(s3 == s4);
	}
	
}
