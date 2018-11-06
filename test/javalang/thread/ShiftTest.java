package javalang.thread;

import java.util.concurrent.atomic.AtomicInteger;

public class ShiftTest {
	
	private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }
    
	public static void main(String[] args) {
		System.out.println(RUNNING);
		System.out.println(SHUTDOWN);
		System.out.println(STOP);
		System.out.println(TIDYING);
		System.out.println(TERMINATED);
//		-536870912
//		0
//		536870912
//		1073741824
//		1610612736
		
		System.out.println();
		
		System.out.println(COUNT_BITS);
		System.out.println(CAPACITY);
		System.out.println(new ShiftTest().ctl.get());
		System.out.println(~CAPACITY);
		
		System.out.println();
		
		int cap = (1 << 4) - 1; // 1111
		System.out.println(cap);
		System.out.println(20 & ~cap);
		System.out.println(20 & cap);
	}

}
