## Executor

执行器，用来执行任务，提供一种将"任务提交"与"任务如何运行"分离开来的机制。

## ExecutorService

ExecutorService继承于Executor。它是"执行者服务"接口，它是为"执行者接口Executor"服务而存在的；

准确的话，ExecutorService提供了"将任务提交给执行者的接口(submit方法)"，"让执行者执行任务(invokeAll, invokeAny方法)"的接口等等。

## Executors

Executors是个静态工厂类。它通过静态工厂方法返回ExecutorService、ScheduledExecutorService、ThreadFactory 和 Callable 等类的对象。

## ThreadPoolExecutor

ThreadPoolExecutor就是大名鼎鼎的"线程池"。它继承于AbstractExecutorService抽象类。

线程池的5种状态是：Running, SHUTDOWN, STOP, TIDYING, TERMINATED。

## ScheduledExecutorService

ScheduledExecutorService是一个接口，它继承于于ExecutorService。它相当于提供了"延时"和"周期执行"功能的ExecutorService。 

ScheduledExecutorService提供了相应的函数接口，可以安排任务在给定的延迟后执行，也可以让任务周期的执行。

## ScheduledThreadPoolExecutor

ScheduledThreadPoolExecutor继承于ThreadPoolExecutor，并且实现了ScheduledExecutorService接口。它相当于提供了"延时"和"周期执行"功能的ScheduledExecutorService。

## AbstractExecutorService.invokeAll()超时模式

- 流程
    - 记录开始时间
    - 执行操作
    - 记录结束时间
    - 计算剩余时间
    - 判断是否已超时

	long nanos = unit.toNanos(timeout);
	long lastTime = System.nanoTime();
    while (condition) {
        doSomething();
        long now = System.nanoTime();
        nanos -= now - lastTime; // 每执行一个任务，计算一次剩余时间
        lastTime = now;
        if (nanos <= 0) // 超时则返回
            return or break;
    }


