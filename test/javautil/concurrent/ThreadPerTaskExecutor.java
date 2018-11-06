package javautil.concurrent;

import java.util.concurrent.Executor;

/**
 * More typically, tasks are executed in some thread other than the caller's thread. 
 * The executor below spawns a new thread for each task. 
 * 
 * @author qiuzj
 *
 */
class ThreadPerTaskExecutor implements Executor {
	public void execute(Runnable r) {
		new Thread(r).start();
	}
}