/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <pre>
 * ArrayBlockingQueue是数组实现的线程安全的有界的阻塞队列。
 * 线程安全是指，ArrayBlockingQueue内部通过“互斥锁”保护竞争资源，实现了多线程对竞争资源的互斥访问。
 * 而有界，则是指ArrayBlockingQueue对应的数组是有界限的。 
 * 阻塞队列，是指多线程访问竞争资源时，当竞争资源已被某线程获取时，其它要获取该资源的线程需要阻塞等待。
 * 而且，ArrayBlockingQueue是按 FIFO（先进先出）原则对元素进行排序，元素都是从尾部插入到队列，从头部开始返回。
 * 注意：ArrayBlockingQueue不同于ConcurrentLinkedQueue，ArrayBlockingQueue是数组实现的，并且是有界限的；
 * 而ConcurrentLinkedQueue是链表实现的，是无界限的。
 * </pre>
 * A bounded {@linkplain BlockingQueue blocking queue} backed by an
 * array.  This queue orders elements FIFO (first-in-first-out).  The
 * <em>head</em> of the queue is that element that has been on the
 * queue the longest time.  The <em>tail</em> of the queue is that
 * element that has been on the queue the shortest time. New elements
 * are inserted at the tail of the queue, and the queue retrieval
 * operations obtain elements at the head of the queue.
 *
 * <p>This is a classic &quot;bounded buffer&quot;, in which a
 * fixed-sized array holds elements inserted by producers and
 * extracted by consumers.  Once created, the capacity cannot be
 * changed.  Attempts to {@code put} an element into a full queue
 * will result in the operation blocking; attempts to {@code take} an
 * element from an empty queue will similarly block.
 *
 * <p>This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to {@code true} grants threads access in FIFO order. Fairness
 * generally decreases throughput but reduces variability and avoids
 * starvation.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /**
     * Serialization ID. This class relies on default serialization
     * even for the items array, which is default-serialized, even if
     * it is empty. Otherwise it could not be declared final, which is
     * necessary here.
     */
    private static final long serialVersionUID = -817911632652898426L;

    /** 
     * 存储队列元素的数组，是个循环数组.
     * <p>
     * The queued items */
    final Object[] items; // Object类型，非泛型. 为何不用泛型？new E[capacity]不合法

    /** 
     * 下一个被取出元素的索引
     * <p>
     * items index for next take, poll, peek or remove */
    int takeIndex;

    /**
     * 下一个被添加元素的索引
     * <p>
     * items index for next put, offer, or add */
    int putIndex;

    /** 
     * 队列中的元素个数
     * <p>
     * Number of elements in the queue */
    int count;

    /*
     * Concurrency control uses the classic two-condition algorithm
     * found in any textbook.
     */

    /** Main lock guarding all access */
    final ReentrantLock lock;
    // Condition是为了更加精细的对锁进行控制
    /** 
     * 如果队列为空，则等待；如果队列不为空，则通知唤醒线程获取元素。（简单理解：等待队列非空，非空时通知唤醒消费线程）
     * <p>
     * Condition for waiting takes */
    private final Condition notEmpty;
    /** 
     * 如果队列满了，则等待；如果队列没有满，则通知唤醒线程将元素入列。（简单理解：等待队列不满，不满时通知唤醒生产线程）
     * <p>
     * Condition for waiting puts */
    private final Condition notFull;

    // Internal helper methods

    /**
     * 循环递增
     * <p>
     * Circularly increment i.
     */
    final int inc(int i) {
        return (++i == items.length) ? 0 : i;
    }

    /**
     * 循环递减
     * <p>
     * Circularly decrement i.
     */
    final int dec(int i) {
        return ((i == 0) ? items.length : i) - 1;
    }

    /**
     * 类型转换, 将对象转换为泛型对应的类型
     * 
     * @param item
     * @return
     */
    @SuppressWarnings("unchecked")
    static <E> E cast(Object item) {
        return (E) item;
    }

    /**
     * 获取指定下标的元素
     * <p>
     * Returns item at index i.
     */
    final E itemAt(int i) {
        return this.<E>cast(items[i]);
    }

    /**
     * Throws NullPointerException if argument is null.
     *
     * @param v the element
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    /**
     * 添加节点公共实现逻辑
     * <p>
     * Inserts element at current put position, advances, and signals.
     * Call only when holding lock.
     */
    private void insert(E x) {
        items[putIndex] = x; // 入列
        putIndex = inc(putIndex); // 更新写索引号
        ++count; // 数量加1
        // 队列非空，唤醒一个处于等待状态的消费线程. 
        // 每次都signal()影响性能吗? 如果本来不需要通知，那么会多两次判断：1.当前线程是否持有独占锁，2.是否有等待获取锁的线程
        notEmpty.signal(); // 由于无法直接判断当前是否有消费线程处理等待状态，所以这里每次都通知一次，在通知方法里本身有判断逻辑，性能基本没什么影响
    }

    /**
     * 获取当前位置的元素
     * <p>
     * Extracts element at current take position, advances, and signals.
     * Call only when holding lock.
     */
    private E extract() {
        final Object[] items = this.items;
        E x = this.<E>cast(items[takeIndex]); // 元素类型转换泛型指定的数据类型
        items[takeIndex] = null; // 将第takeIndex元素设为null，即删除。同时help GC
        takeIndex = inc(takeIndex); // 更新读索引号
        --count; // 数量减1
        notFull.signal(); // 队列未满，唤醒一个处于等待状态的生产线程
        return x;
    }

    /**
     * 删除指定索引号的元素
     * <p>
     * Deletes item at position i.
     * Utility for remove and iterator.remove.
     * Call only when holding lock.
     */
    void removeAt(int i) {
        final Object[] items = this.items;
        // 如果删除的元素刚好是列头
        // if removing front item, just advance
        if (i == takeIndex) {
            items[takeIndex] = null; // help GC
            takeIndex = inc(takeIndex); // 更新读索引号
        }
        // 如果删除的元素非列头元素
        else {
        	// 循环将i之后的元素往前移动1位（从putIndex往takeIndex方向移动）
            // slide over all others up through putIndex.
            for (;;) {
                int nexti = inc(i); // 下一个元素
                // 没有到列尾
                if (nexti != putIndex) {
                    items[i] = items[nexti]; // 将元素前移
                    i = nexti; // i指向下一个元素
                }
                // 已到列尾
                else {
                    items[i] = null; // help GC
                    putIndex = i; // 更新写索引号
                    break;
                }
            }
        }
        --count; // 数量减1
        notFull.signal(); // 队列未满，唤醒一个处于等待状态的生产线程
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity and default access policy.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public ArrayBlockingQueue(int capacity) {
        this(capacity, false);
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity and the specified access policy.
     *
     * @param capacity the capacity of this queue
     * @param fair if {@code true} then queue accesses for threads blocked
     *        on insertion or removal, are processed in FIFO order;
     *        if {@code false} the access order is unspecified.
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public ArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.items = new Object[capacity];
        lock = new ReentrantLock(fair);
        notEmpty = lock.newCondition();
        notFull =  lock.newCondition();
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity, the specified access policy and initially containing the
     * elements of the given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param capacity the capacity of this queue
     * @param fair if {@code true} then queue accesses for threads blocked
     *        on insertion or removal, are processed in FIFO order;
     *        if {@code false} the access order is unspecified.
     * @param c the collection of elements to initially contain
     * @throws IllegalArgumentException if {@code capacity} is less than
     *         {@code c.size()}, or less than 1.
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public ArrayBlockingQueue(int capacity, boolean fair,
                              Collection<? extends E> c) {
        this(capacity, fair);

        final ReentrantLock lock = this.lock;
        lock.lock(); // Lock only for visibility, not mutual exclusion
        try {
            int i = 0;
            try {
            	// 将初始化集合c中的元素逐个放到items数组中
                for (E e : c) {
                    checkNotNull(e);
                    items[i++] = e;
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                throw new IllegalArgumentException();
            }
            count = i;
            putIndex = (i == capacity) ? 0 : i;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 添加元素到队列里，添加成功返回true，由于容量满了添加失败会抛出IllegalStateException异常。
     * 内部实现基于offer(E e)
     * <p>
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and throwing an
     * {@code IllegalStateException} if this queue is full.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws IllegalStateException if this queue is full
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return super.add(e);
    }

    /**
     * 添加元素到队列里，添加成功返回true，添加失败返回false
     * <p>
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and {@code false} if this queue
     * is full.  This method is generally preferable to method {@link #add},
     * which can fail to insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == items.length) // 队列已满，添加失败
                return false;
            else {
                insert(e); // 队列未满，添加元素
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 添加元素到队列里，如果容量满了会阻塞直到容量不满
     * <p>
     * Inserts the specified element at the tail of this queue, waiting
     * for space to become available if the queue is full.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
        	// 如果队列满了，就死等，被唤醒之后，还要再次检查，如此直到队列未满为止
            while (count == items.length)
                notFull.await(); // 等待未满
            insert(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * up to the specified wait time for space to become available if
     * the queue is full.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {

        checkNotNull(e);
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            insert(e);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (count == 0) ? null : extract(); // 队列非空时获取
        } finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
        	// 如果队列为空，就死等，被唤醒之后，还要再次检查，如此直到队列非空为止
            while (count == 0)
                notEmpty.await();
            return extract();
        } finally {
            lock.unlock();
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout); // 时间转换为纳秒
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
        	// 如果队列为空，则超时等待
            while (count == 0) {
                if (nanos <= 0) // 获取超时处理
                    return null;
                nanos = notEmpty.awaitNanos(nanos); // 超时等待
            }
            return extract();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取头元素，但不删除
     */
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (count == 0) ? null : itemAt(takeIndex);
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.length - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * <p>Removal of interior elements in circular array based queues
     * is an intrinsically slow and disruptive operation, so should
     * be undertaken only in exceptional circumstances, ideally
     * only when the queue is known not to be accessible by other
     * threads.
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o == null) return false;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	// 遍历队列，总共有count个元素，从takeIndex开始遍历，找到则删除指定索引的元素
            for (int i = takeIndex, k = count; k > 0; i = inc(i), k--) {
                if (o.equals(items[i])) { // found it
                    removeAt(i);
                    return true;
                }
            }
            return false; // not found
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	// 遍历数组，逐个比较
            for (int i = takeIndex, k = count; k > 0; i = inc(i), k--)
                if (o.equals(items[i]))
                    return true;
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回一个按顺序存放的数组
     * <p>
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final int count = this.count;
            Object[] a = new Object[count];
            // 遍历所有元素，将元素逐个放到新数组中：按顺序存放
            for (int i = takeIndex, k = 0; k < count; i = inc(i), k++)
                a[k] = items[i];
            return a;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final int count = this.count;
            final int len = a.length;
            if (len < count) // 如果传入的数组对象比实际需要的小，则按实际容量重新分配一个数组
                a = (T[])java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(), count);
            for (int i = takeIndex, k = 0; k < count; i = inc(i), k++)
                a[k] = (T) items[i];
            if (len > count)
                a[count] = null; // 最后一个有效元素的下一个元素置null
            return a;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int k = count;
            if (k == 0)
                return "[]";

            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = takeIndex; ; i = inc(i)) {
                Object e = items[i];
                sb.append(e == this ? "(this Collection)" : e);
                if (--k == 0)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空
     * <p>
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (int i = takeIndex, k = count; k > 0; i = inc(i), k--)
                items[i] = null;
            count = 0;
            putIndex = 0;
            takeIndex = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        checkNotNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int n = 0;
            int max = count;
            // 将所有元素存入集合c
            while (n < max) {
                c.add(this.<E>cast(items[i]));
                items[i] = null;
                i = inc(i);
                ++n;
            }
            // 重置相关参数
            if (n > 0) {
                count = 0;
                putIndex = 0;
                takeIndex = 0;
                notFull.signalAll();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        checkNotNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int n = 0;
            int max = (maxElements < count) ? maxElements : count;
            // 将max个元素添加到集合c中
            while (n < max) {
                c.add(this.<E>cast(items[i]));
                items[i] = null;
                i = inc(i);
                ++n;
            }
            if (n > 0) {
                count -= n; // 更新剩余数量
                takeIndex = i; // 更新下一个被取出元素的索引号
                notFull.signalAll(); // 通知所有生产者
            }
            return n; // 获取元素的数量
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned {@code Iterator} is a "weakly consistent" iterator that
     * will never throw {@link java.util.ConcurrentModificationException
     * ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Iterator for ArrayBlockingQueue. To maintain weak consistency
     * with respect to puts and takes, we (1) read ahead one slot, so
     * as to not report hasNext true but then not have an element to
     * return -- however we later recheck this slot to use the most
     * current value; (2) ensure that each array slot is traversed at
     * most once (by tracking "remaining" elements); (3) skip over
     * null slots, which can occur if takes race ahead of iterators.
     * However, for circular array-based queues, we cannot rely on any
     * well established definition of what it means to be weakly
     * consistent with respect to interior removes since these may
     * require slot overwrites in the process of sliding elements to
     * cover gaps. So we settle for resiliency, operating on
     * established apparent nexts, which may miss some elements that
     * have moved between calls to next.
     */
    private class Itr implements Iterator<E> {
        private int remaining; // Number of elements yet to be returned. 相当于count
        private int nextIndex; // Index of element to be returned by next. 相当于takeIndex
        private E nextItem;    // Element to be returned by next call to next. 下一个元素
        private E lastItem;    // Element returned by last call to next. 最后一次通过next返回的元素
        private int lastRet;   // Index of last element returned, or -1 if none. 最后一次返回的元素索引

        Itr() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                lastRet = -1;
                if ((remaining = count) > 0)
                    nextItem = itemAt(nextIndex = takeIndex); // 初始化下一个元素（第一个元素）
            } finally {
                lock.unlock();
            }
        }

        public boolean hasNext() {
            return remaining > 0;
        }

        /**
         * 获取下一个元素. 需要记录最后一次返回的元素及其索引、下一个元素及其索引
         * 写得这么复杂，个人理解有两点：
         * <ol>
         * <li>允许获取旧值
         * <li>忽略为null的元素
         * </ol>
         */
        public E next() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (remaining <= 0)
                    throw new NoSuchElementException();
                lastRet = nextIndex;
                E x = itemAt(nextIndex);  // check for fresher value. 获取需要返回的元素
                if (x == null) {
                    x = nextItem;         // we are forced to report old value. 实在没办法只能返回旧值了
                    lastItem = null;      // but ensure remove fails
                }
                else
                    lastItem = x;
                // 忽略值为null的元素，更新remaining、nextIndex、nextItem
                // 分为几步（其他就是通过while获取下一个不为null的元素，关键是为什么中间会出现为null的元素？）：
                // 先判断是否还有元素
                // 计算下一个元素的索引号
                // 获取下一个元素
                // 如果下一个元素为空，则继续取下一个不为空的元素
                while (--remaining > 0 && // skip over nulls
                       (nextItem = itemAt(nextIndex = inc(nextIndex))) == null)
                    ;
                return x;
            } finally {
                lock.unlock();
            }
        }

        public void remove() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                int i = lastRet;
                if (i == -1)
                    throw new IllegalStateException();
                lastRet = -1; // 重置最后一个返回的元素索引号
                
                E x = lastItem;
                lastItem = null; // 重置最后一个返回的元素
                
                // only remove if item still at index. 确保元素仍在原来的索引上
                if (x != null && x == items[i]) {
                    boolean removingHead = (i == takeIndex); // 删除的元素是否为头节点
                    removeAt(i);
                    if (!removingHead)
                        nextIndex = dec(nextIndex); // 由于removeAt使得在i之后的元素往前移动了一位，所以nextIndex需要-1
                }
            } finally {
                lock.unlock();
            }
        }
    }

}
