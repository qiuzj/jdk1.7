package javautil.concurrent;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * 同步执行任务，顺序执行.
 * 
 * Many Executor implementations impose some sort of limitation on how and when tasks are scheduled. 
 * The executor below serializes the submission of tasks to a second executor, illustrating a composite executor. 
 * 
 * @author qiuzj
 *
 */
class SerialExecutor implements Executor {
	final Queue<Runnable> tasks = new ArrayDeque<>(); // 任务队列
	final Executor executor; // 真正用于执行任务的线程池
	Runnable active; // 当前执行任务

	SerialExecutor(Executor executor) {
		this.executor = executor;
	}

	public synchronized void execute(final Runnable r) {
		// 提交的任务先放入队列
		tasks.offer(new Runnable() {
			public void run() {
				try {
					r.run();
				} finally {
					scheduleNext(); // 多包装一层可以执行scheduleNext通知执行下一个任务
				}
			}
		});
		// 首次执行active为null，将触发scheduleNext启动顺序执行任务
		if (active == null) {
			scheduleNext();
		}
	}

	protected synchronized void scheduleNext() {
		if ((active = tasks.poll()) != null) {
			executor.execute(active);
		}
	}
}