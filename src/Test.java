import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Test {

	public static void main1(String[] args) {
		for (int i = 0; i < 5; i++) {
			new Thread("Thread-" + i) {
				public void run() {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				};
			}.start();
		}
		
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		Thread[] threads = new Thread[tg.activeCount()];
		tg.enumerate(threads);
		
		System.out.println(threads.length);
		for (Thread t : threads) {
			System.out.println(t.isAlive() + " " + t.isDaemon() + " " + t.isInterrupted());
		}
		
		System.out.println();
		tg.interrupt();
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
		for (Thread t : threads) {
			System.out.println(t.isAlive() + " " + t.isDaemon() + " " + t.isInterrupted());
		}
	}
	
	public static void main(String[] args) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        final ThreadGroup group = new ThreadGroup("Main_Test_Group");
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(group, new Runnable() {

                @Override
                public void run() {
                    int sleep = (int)(Math.random() * 10);
                    try {
                        Thread.sleep(1000 * 3);
                        System.out.println(Thread.currentThread().getName()+"执行完毕");
                        System.out.println("当前线程组中的运行线程数"+group.activeCount());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, group.getName()+" #"+i+"");
            pool.execute(thread);
        }
    }

}
