
LinkedBlockingQueue与ArrayBlockingQueue在通知方面有比较大的区别：
- ArrayBlockingQueue每次生产或消费的时候，会互相signal通知对方
- LinkedBlockingQueue仅在生产时由0到1、消费时由满到非满，才会signal通知对方；剩下的通知由生产通知自己，消费通知自己。

这么设计是什么原因？

另一方面，在锁的设计上也有区别：
- ArrayBlockingQueue只使用一把锁，notEmpty、notFull共用
    - 生产和消费进行了同步，不能同时进行
    - 假设用两把锁，生产者与消费者之间将出现不同步问题，可能导致一方在signal的时候，另一方刚好在稍后wait了，这样就导致总有线程一直处于wait状态
- LinkedBlockingQueue使用了两把锁,，notEmpty、notFull不共用锁
    - 提高并发能力，生产和消费可以同时进行
    - 如何避免类似于ArrayBlockingQueue的同步问题？既然signal和wait会出现不同步的问题，那么就对他们进行加锁同步即可。见signalNotEmpty()、signalNotFull()方法

一个lock的设计方式较为简单。

takeLock

notEmpty

putLock

notFull