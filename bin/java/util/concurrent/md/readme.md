## Queue

**入列**

offer：非阻塞式，success return true or fail return false（if full）

add：非阻塞式，success return void or fail throws exception（if full，base on offer method）

put：阻塞式，队列满则等待，直至成功

**出列**

poll：非阻塞式，返回节点值或null（如果队列为空）

remove：非阻塞式，要么返回节点值，要么失败抛出异常（如果队列为空，base on poll method）

take：阻塞式，队列为空则等待，直到成功


## 模式

**阻塞等待**

ReentrantLock lock = new ReentrantLock();  
Condition condition = lock.newCondition();  

lock.lockInterruptibly(); // 防止await()没被唤醒？？  
while (exec process logic and the result is false) {  
    condition.await();  
}

**超时等待1**

long nanos = init timeout;  
ReentrantLock lock = new ReentrantLock();  
Condition condition = lock.newCondition();  

lock.lockInterruptibly(); // 防止await()没被唤醒？？  
while (exec process logic and the result is false) {  
    if (nanos <= 0)  
        return false;  
    nanos = condition.awaitNanos(nanos);  
}

**超时等待2**

long nanosTimeout = init timeout;  
long lastTime = System.nanoTime();  

for (;;) {  
    if (exec process logic and the result is true)  
        return or break;  
    if (nanosTimeout <= 0)  
        return false;  
    LockSupport.parkNanos(this, nanosTimeout);  
    long now = System.nanoTime();  
    nanosTimeout -= now - lastTime;  
    lastTime = now;  
}

lockInterruptibly被interrupt之后，锁是如何释放的？

Object和Thread的WAITING、TIMED_WAITING方法都支持throws InterruptedException


定义变量时，何时用volatile，原则是什么？count基本都是transient



