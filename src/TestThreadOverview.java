import java.util.concurrent.locks.LockSupport;

public class TestThreadOverview {
    private String str1 = "str1";
    private String str2 = "str2";
 
    public static void main(String[] args) {
        TestThreadOverview test = new TestThreadOverview();
         
        String local_str = "local_str";
         
        LockSupport.park();
    }
}