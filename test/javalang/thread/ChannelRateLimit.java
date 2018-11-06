package javalang.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ChannelRateLimit {

	private BlockingQueue<String> queue;
	
	private Thread syncThread;
	
	public ChannelRateLimit() {
		queue = new LinkedBlockingQueue<>(10000);
	}
	
	private void initSyncThread() {
		syncThread = new Thread(new SyncWorker(), "Thread-Worker");
		syncThread.start();
	}
	
	public void acquire(int permits) {
		
	}
	
	class SyncWorker implements Runnable {

		@Override
		public void run() {
			while (true) {
				String channel;
				try {
					channel = queue.take();
					System.out.println(channel);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	class Producer implements Runnable {

		@Override
		public void run() {
			int i = 0;
			while (true) {
				queue.offer("chl" + i++);
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (i % 20 == 0) {
					syncThread.interrupt();
				}
			}
		}
		
	}
	
	private void initProducer() {
		new Thread(new Producer(), "Thread-Producer").start();
	}
	
	public static void main(String[] args) {
		ChannelRateLimit obj = new ChannelRateLimit();
		obj.initSyncThread();
		obj.initProducer();
		
		try {
			TimeUnit.MILLISECONDS.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
}
