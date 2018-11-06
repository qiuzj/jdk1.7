package javautil.concurrent;

import java.util.concurrent.Executor;

/**
 * However, the Executor interface does not strictly require that execution be asynchronous. 
 * In the simplest case, an executor can run the submitted task immediately in the caller's thread:
 *  
 * @author qiuzj
 *
 */
class DirectExecutor implements Executor {
	public void execute(Runnable r) {
		r.run();
	}
}