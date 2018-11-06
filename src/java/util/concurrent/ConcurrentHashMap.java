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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <pre>
 * ConcurrentHashMap是线程安全的哈希表。HashMap, Hashtable, ConcurrentHashMap之间的关联如下：
 * (01) HashMap是非线程安全的哈希表，常用于单线程程序中。
 * (02) Hashtable是线程安全的哈希表，它是通过synchronized来保证线程安全的。
 *      即，多线程通过同一个“对象的同步锁”来实现并发控制。Hashtable在线程竞争激烈时，效率比较低(此时建议使用ConcurrentHashMap)！因为当一个线程访问Hashtable的同步方法时，其它线程就访问Hashtable的同步方法时，可能会进入阻塞状态。
 * (03) ConcurrentHashMap是线程安全的哈希表，它是通过“锁分段”来保证线程安全的。
 *     ConcurrentHashMap将哈希表分成许多片段(Segment)，每一个片段除了保存哈希表之外，本质上也是一个“可重入的互斥锁”(ReentrantLock)。多线程对同一个片段的访问，是互斥的；但是，对于不同片段的访问，却是可以同步进行的。
 * </pre>
 * A hash table supporting full concurrency of retrievals and
 * adjustable expected concurrency for updates. This class obeys the
 * same functional specification as {@link java.util.Hashtable}, and
 * includes versions of methods corresponding to each method of
 * <tt>Hashtable</tt>. However, even though all operations are
 * thread-safe, retrieval operations do <em>not</em> entail locking,
 * and there is <em>not</em> any support for locking the entire table
 * in a way that prevents all access.  This class is fully
 * interoperable with <tt>Hashtable</tt> in programs that rely on its
 * thread safety but not on its synchronization details.
 *
 * <p> Retrieval operations (including <tt>get</tt>) generally do not
 * block, so may overlap with update operations (including
 * <tt>put</tt> and <tt>remove</tt>). Retrievals reflect the results
 * of the most recently <em>completed</em> update operations holding
 * upon their onset.  For aggregate operations such as <tt>putAll</tt>
 * and <tt>clear</tt>, concurrent retrievals may reflect insertion or
 * removal of only some entries.  Similarly, Iterators and
 * Enumerations return elements reflecting the state of the hash table
 * at some point at or since the creation of the iterator/enumeration.
 * They do <em>not</em> throw {@link ConcurrentModificationException}.
 * However, iterators are designed to be used by only one thread at a time.
 *
 * <p> The allowed concurrency among update operations is guided by
 * the optional <tt>concurrencyLevel</tt> constructor argument
 * (default <tt>16</tt>), which is used as a hint for internal sizing.  The
 * table is internally partitioned to try to permit the indicated
 * number of concurrent updates without contention. Because placement
 * in hash tables is essentially random, the actual concurrency will
 * vary.  Ideally, you should choose a value to accommodate as many
 * threads as will ever concurrently modify the table. Using a
 * significantly higher value than you need can waste space and time,
 * and a significantly lower value can lead to thread contention. But
 * overestimates and underestimates within an order of magnitude do
 * not usually have much noticeable impact. A value of one is
 * appropriate when it is known that only one thread will modify and
 * all others will only read. Also, resizing this or any other kind of
 * hash table is a relatively slow operation, so, when possible, it is
 * a good idea to provide estimates of expected table sizes in
 * constructors.
 *
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 *
 * <p> Like {@link Hashtable} but unlike {@link HashMap}, this class
 * does <em>not</em> allow <tt>null</tt> to be used as a key or value.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
        implements ConcurrentMap<K, V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    /*
     * The basic strategy is to subdivide the table among Segments,
     * each of which itself is a concurrently readable hash table.  To
     * reduce footprint, all but one segments are constructed only
     * when first needed (see ensureSegment). To maintain visibility
     * in the presence of lazy construction, accesses to segments as
     * well as elements of segment's table must use volatile access,
     * which is done via Unsafe within methods segmentAt etc
     * below. These provide the functionality of AtomicReferenceArrays
     * but reduce the levels of indirection. Additionally,
     * volatile-writes of table elements and entry "next" fields
     * within locked operations use the cheaper "lazySet" forms of
     * writes (via putOrderedObject) because these writes are always
     * followed by lock releases that maintain sequential consistency
     * of table updates.
     *
     * Historical note: The previous version of this class relied
     * heavily on "final" fields, which avoided some volatile reads at
     * the expense of a large initial footprint.  Some remnants of
     * that design (including forced construction of segment 0) exist
     * to ensure serialization compatibility.
     */

    /* ---------------- Constants -------------- */

    /**
     * The default initial capacity for this table,
     * used when not otherwise specified in a constructor.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The default load factor for this table, used when not
     * otherwise specified in a constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default concurrency level for this table, used when not
     * otherwise specified in a constructor.
     */
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two <= 1<<30 to ensure that entries are indexable
     * using ints.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 每个Segment的最小容量
     * <p>
     * The minimum capacity for per-segment tables.  Must be a power
     * of two, at least two to avoid immediate resizing on next use
     * after lazy construction.
     */
    static final int MIN_SEGMENT_TABLE_CAPACITY = 2;

    /**
     * 最大分段数.
     * <p>
     * The maximum number of segments to allow; used to bound
     * constructor arguments. Must be power of two less than 1 << 24.
     */
    static final int MAX_SEGMENTS = 1 << 16; // slightly conservative. 65536

    /**
     * Number of unsynchronized retries in size and containsValue
     * methods before resorting to locking. This is used to avoid
     * unbounded retries if tables undergo continuous modification
     * which would make it impossible to obtain an accurate result.
     */
    static final int RETRIES_BEFORE_LOCK = 2;

    /* ---------------- Fields -------------- */

    /**
     * 保存在VM引导之后无法初始化的值。
     * <p>
     * holds values which can't be initialized until after VM is booted.
     */
    private static class Holder {

        /**
        * Enable alternative hashing of String keys?
        *
        * <p>Unlike the other hash map implementations we do not implement a
        * threshold for regulating whether alternative hashing is used for
        * String keys. Alternative hashing is either enabled for all instances
        * or disabled for all instances.
        */
        static final boolean ALTERNATIVE_HASHING;

        static {
            // Use the "threshold" system property even though our threshold
            // behaviour is "ON" or "OFF".
            String altThreshold = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction(
                    "jdk.map.althashing.threshold"));

            int threshold;
            try {
                threshold = (null != altThreshold)
                        ? Integer.parseInt(altThreshold)
                        : Integer.MAX_VALUE;

                // disable alternative hashing if -1
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }

                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch(IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }
            ALTERNATIVE_HASHING = threshold <= MAXIMUM_CAPACITY;
        }
    }

    /**
     * A randomizing value associated with this instance that is applied to
     * hash code of keys to make hash collisions harder to find.
     */
    private transient final int hashSeed = randomHashSeed(this);

    private static int randomHashSeed(ConcurrentHashMap instance) {
        if (sun.misc.VM.isBooted() && Holder.ALTERNATIVE_HASHING) {
            return sun.misc.Hashing.randomHashSeed(instance);
        }

        return 0;
    }

    /**
     * 分段的索引掩码值(segments.length-1). 根据默认构造函数，默认为15. 取值范围为0、1、3、7、15、31、63~65535
     * <p>
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     */
    final int segmentMask;

    /**
     * 分段的索引位移量. 根据默认构造函数，默认为28. 取值范围为16~32.
     * 用于将高segmentMask位移至低位，再与segmentMask相与获取哈希码
     * <p>
     * Shift value for indexing within segments.
     */
    final int segmentShift;

    /**
     * 分段数组
     * <p>
     * The segments, each of which is a specialized hash table.
     */
    final Segment<K,V>[] segments;

    transient Set<K> keySet;
    transient Set<Map.Entry<K,V>> entrySet;
    transient Collection<V> values;

    /**
     * 单向链表节点.
     * 
     * ConcurrentHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry.
     */
    static final class HashEntry<K,V> {
    	/**
    	 * 哈希值
    	 */
        final int hash;
        /**
         * 键
         */
        final K key;
        /**
         * 值
         */
        volatile V value; // volatile保证可见性, get时不需要加锁
        /**
         * 下一个节点
         */
        volatile HashEntry<K,V> next; // volatile

        HashEntry(int hash, K key, V value, HashEntry<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        /**
         * Sets next field with volatile write semantics.  (See above
         * about use of putOrderedObject.)
         */
        final void setNext(HashEntry<K,V> n) {
            UNSAFE.putOrderedObject(this, nextOffset, n); // StoreStore
        }

        // Unsafe mechanics
        static final sun.misc.Unsafe UNSAFE;
        /**
         * next变量的内存偏移量
         */
        static final long nextOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class k = HashEntry.class;
                nextOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * 获取给定HashEntry的第i个元素（如果非空），具有volatile的读取语义。
     * <p>
     * Gets the ith element of given table (if nonnull) with volatile
     * read semantics. Note: This is manually integrated into a few
     * performance-sensitive methods to reduce call overhead.
     */
    @SuppressWarnings("unchecked")
    static final <K,V> HashEntry<K,V> entryAt(HashEntry<K,V>[] tab, int i) { // why getObjectVolatile? faster then getObject?
        return (tab == null) ? null :
            (HashEntry<K,V>) UNSAFE.getObjectVolatile
            (tab, ((long)i << TSHIFT) + TBASE);
    }

    /**
     * Sets the ith element of given table, with volatile write
     * semantics. (See above about use of putOrderedObject.)
     */
    static final <K,V> void setEntryAt(HashEntry<K,V>[] tab, int i,
                                       HashEntry<K,V> e) {
        UNSAFE.putOrderedObject(tab, ((long)i << TSHIFT) + TBASE, e);
    }

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because ConcurrentHashMap uses power-of-two length hash tables,
     * that otherwise encounter collisions for hashCodes that do not
     * differ in lower or upper bits.
     */
    private int hash(Object k) { // 通过再哈希，减少哈希冲突，使元素能够均匀的分布
        int h = hashSeed;

        if ((0 != h) && (k instanceof String)) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        h ^= k.hashCode();

        // single-word Wang/Jenkins hash的变种算法
        // Spread bits to regularize both segment and index locations,
        // using variant of single-word Wang/Jenkins hash.
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);
        return h ^ (h >>> 16);
    }

    /**
     * “锁分段”对应的存储结构.
     * Segment本质上是一个可重入的互斥锁。
     * 
     * Segments are specialized versions of hash tables.  This
     * subclasses from ReentrantLock opportunistically, just to
     * simplify some locking and avoid separate construction.
     */
    static final class Segment<K,V> extends ReentrantLock implements Serializable {
        /*
         * Segments maintain a table of entry lists that are always
         * kept in a consistent state, so can be read (via volatile
         * reads of segments and tables) without locking.  This
         * requires replicating nodes when necessary during table
         * resizing, so the old lists can be traversed by readers
         * still using old version of table.
         *
         * This class defines only mutative methods requiring locking.
         * Except as noted, the methods of this class perform the
         * per-segment versions of ConcurrentHashMap methods.  (Other
         * methods are integrated directly into ConcurrentHashMap
         * methods.) These mutative methods use a form of controlled
         * spinning on contention via methods scanAndLock and
         * scanAndLockForPut. These intersperse tryLocks with
         * traversals to locate nodes.  The main benefit is to absorb
         * cache misses (which are very common for hash tables) while
         * obtaining locks so that traversal is faster once
         * acquired. We do not actually use the found nodes since they
         * must be re-acquired under lock anyway to ensure sequential
         * consistency of updates (and in any case may be undetectably
         * stale), but they will normally be much faster to re-locate.
         * Also, scanAndLockForPut speculatively creates a fresh node
         * to use in put if no node is found.
         */

        private static final long serialVersionUID = 2249069246763182397L;

        /**
         * The maximum number of times to tryLock in a prescan before
         * possibly blocking on acquire in preparation for a locked
         * segment operation. On multiprocessors, using a bounded
         * number of retries maintains cache acquired while locating
         * nodes.
         */
        static final int MAX_SCAN_RETRIES =
            Runtime.getRuntime().availableProcessors() > 1 ? 64 : 1;

        /**
         * 哈希桶数组.
	     * 存储数据的HashEntry数组，长度是2的幂。<br>
	     * 采用拉链法实现的，每一个Entry本质上是一个单向链表。<br>
	     * 单向链表，哈希表的"key-value键值对"都是存储在数组中，是对Map.Entry的实现
	     * <p>
         * The per-segment table. Elements are accessed via
         * entryAt/setEntryAt providing volatile semantics.
         */
        transient volatile HashEntry<K,V>[] table;

        /**
         * Segment元素的大小，它是Segment保存的键值对的数量
         * <p>
         * The number of elements. Accessed only either within locks
         * or among other volatile reads that maintain visibility.
         */
        transient int count;

        /**
         * Segment内部结构发生变化的次数（覆盖值不属于结构变化）. 用来实现fail-fast机制
         * <p>
         * The total number of mutative operations in this segment.
         * Even though this may overflows 32 bits, it provides
         * sufficient accuracy for stability checks in CHM isEmpty()
         * and size() methods.  Accessed only either within locks or
         * among other volatile reads that maintain visibility.
         */
        transient int modCount;

        /**
         * 容量阈值，是哈希表在其容量自动增加之前可以达到多满的一种尺度。
         * <p>
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always <tt>(int)(capacity *
         * loadFactor)</tt>.)
         */
        transient int threshold;

        /**
         * 加载因子
         * <p>
         * The load factor for the hash table.  Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         * @serial
         */
        final float loadFactor;

        Segment(float lf, int threshold, HashEntry<K,V>[] tab) {
            this.loadFactor = lf;
            this.threshold = threshold;
            this.table = tab;
        }

        final V put(K key, int hash, V value, boolean onlyIfAbsent) {
        	// tryLock()获取锁，成功返回true，失败返回false。
            // 获取锁失败的话，则通过scanAndLockForPut()获取锁，并返回”要插入的key-value“对应的”HashEntry链表“。
            HashEntry<K,V> node = tryLock() ? null :
                scanAndLockForPut(key, hash, value); // 如果tryLock()获得锁，此时node为null
            V oldValue;
            try {
            	// tab代表”当前Segment中的HashEntry数组“
                HashEntry<K,V>[] tab = table;
                int index = (tab.length - 1) & hash; // 计算节点对应的HashEntry索引号
                HashEntry<K,V> first = entryAt(tab, index); // 根据“hash值”获取“当前Segment的HashEntry数组对象”中的“HashEntry节点”
                for (HashEntry<K,V> e = first;;) {
                	// 如果未到达链尾，则比较节点key是否已存在
                    if (e != null) {
                        K k;
                        // 当”要插入的key-value键值对“已经存在于”HashEntry链表中“时，先保存原有的值。
                        // 若”onlyIfAbsent“为true，即”要插入的key不存在时才插入”，则直接退出；
                        // 否则，用新的value值覆盖原有的原有的值。
                        if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k))) {
                            oldValue = e.value; // 保存旧值
                            // 根据标志位确认元素存在时是否需要更新值
                            if (!onlyIfAbsent) {
                                e.value = value;
                                ++modCount; // 为什么只是值改变了这里却要++
                            }
                            break;
                        }
                        e = e.next;
                    }
                    // 如果到达链尾仍未找到，则添加新节点
                    else {
                    	// 如果node非空，则将first设置为“node的下一个节点”。
                        // 否则，新建HashEntry链表
                        if (node != null) // 当node!=null时，即在scanAndLockForPut()获取锁时，已经新建了key-value对应的HashEntry节点，则”将HashEntry添加到Segment中“
                            node.setNext(first);
                        else // 否则，新建key-value对应的HashEntry节点，然后再“将HashEntry添加到Segment中”
                            node = new HashEntry<K,V>(hash, key, value, first);
                        
                        int c = count + 1;
                        // 如果添加key-value键值对之后，Segment中的元素超过阈值(并且，HashEntry数组的长度没超过限制)，则rehash；
                        // 否则，直接添加key-value键值对。
                        if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                            rehash(node);
                        else
                            setEntryAt(tab, index, node); // 新增节点作为原桶位的首节点
                        ++modCount;
                        count = c;
                        oldValue = null; // 无旧值
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        /**
         * Doubles size of table and repacks entries, also adding the
         * given node to new table
         */
        @SuppressWarnings("unchecked")
        private void rehash(HashEntry<K,V> node) {
            /*
             * Reclassify nodes in each list to new table.  Because we
             * are using power-of-two expansion, the elements from
             * each bin must either stay at same index, or move with a
             * power of two offset. We eliminate unnecessary node
             * creation by catching cases where old nodes can be
             * reused because their next fields won't change.
             * Statistically, at the default threshold, only about
             * one-sixth of them need cloning when a table
             * doubles. The nodes they replace will be garbage
             * collectable as soon as they are no longer referenced by
             * any reader thread that may be in the midst of
             * concurrently traversing table. Entry accesses use plain
             * array indexing because they are followed by volatile
             * table write.
             */
            HashEntry<K,V>[] oldTable = table;
            int oldCapacity = oldTable.length;
            
            int newCapacity = oldCapacity << 1; // 容量扩大1倍
            threshold = (int)(newCapacity * loadFactor); // 新阈值
            HashEntry<K,V>[] newTable = (HashEntry<K,V>[]) new HashEntry[newCapacity]; // 新数组
            int sizeMask = newCapacity - 1; // 新的掩码
            
            // 遍历”原始的HashEntry数组“，
            // 将”原始的HashEntry数组“中的每个”HashEntry链表“的值，都复制到”新的HashEntry数组的HashEntry元素“中。
            for (int i = 0; i < oldCapacity ; i++) {
                HashEntry<K,V> e = oldTable[i]; // 头节点
                if (e != null) { // 每次处理一个HashEntry链表，非空的才需要处理
                    HashEntry<K,V> next = e.next; // 第二个节点
                    int idx = e.hash & sizeMask; // 新数组下标
                    
                    // 如果仅有一个节点，只直接放到新table
                    // 因为Segment中数组的长度也永远是2的倍数，而将数组长度扩大成原来的2倍，
                    // 那么新节点在新数组中的位置只能是相同的索引号或者原来索引号加原来数组的长度，因而可以保证每条链在rehash是不会相互干扰
                    if (next == null)   //  Single node on list
                        newTable[idx] = e;
                    else { // Reuse consecutive sequence at same slot
                        HashEntry<K,V> lastRun = e;
                        int lastIdx = idx;
                        
                        // 定位第一个后续所有节点在扩容后index都保持不变的节点，然后将这个节点之前的所有节点重排即可。
                        for (HashEntry<K,V> last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }
                        newTable[lastIdx] = lastRun; // 将lastRun及节点后面索引都相同的链表放到lastIdx下标的位桶中
                        
                        // rehash处理lastRun之前的节点
                        // 将”原始的HashEntry数组“中的”HashEntry链表(e)“的值，都复制到”新的HashEntry数组的HashEntry“中。
                        // Clone remaining nodes
                        for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
                            V v = p.value;
                            int h = p.hash;
                            int k = h & sizeMask;
                            HashEntry<K,V> n = newTable[k]; // 下标k所在位桶的第一个元素，即HashEntry头节点
                            newTable[k] = new HashEntry<K,V>(h, p.key, v, n); // 新节点插入作为头节点，原头节点作为next节点
                        }
                    }
                }
            }
            // 将新的node节点添加到“Segment的新HashEntry数组(newTable)“中。
            int nodeIndex = node.hash & sizeMask; // add the new node
            node.setNext(newTable[nodeIndex]);
            newTable[nodeIndex] = node;
            table = newTable; // table指向新的数组
        }

        /**
         * 自旋获取锁，如果节点不存在则创建节点
         * <p>
         * Scans for a node containing given key while trying to
         * acquire lock, creating and returning one if not found. Upon
         * return, guarantees that lock is held. UNlike in most
         * methods, calls to method equals are not screened: Since
         * traversal speed doesn't matter, we might as well help warm
         * up the associated code and accesses as well.
         *
         * @return a new node if key not found, else null
         */
        private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value) {
        	// 第一个HashEntry节点
            HashEntry<K,V> first = entryForHash(this, hash);
            // 当前的HashEntry节点
            HashEntry<K,V> e = first;
            HashEntry<K,V> node = null;
            // 重复计数(自旋计数器)
            int retries = -1; // negative while locating node
            
            // 查找”key-value键值对“在”HashEntry链表上对应的节点“；
            // 若找到的话，则不断的自旋；在自旋期间，若通过tryLock()获取锁成功则返回；否则自旋MAX_SCAN_RETRIES次数之后，强制获取”锁“并退出。
            // 若没有找到的话，则新建一个HashEntry链表。然后不断的自旋。
            // 此外，若在自旋期间，HashEntry链表的表头发生变化；则重新进行查找和自旋工作！
            while (!tryLock()) { // 自旋尝试获取锁，在没获取到锁的时候，有空做一些检查和准备工作（如创建节点）；如果自旋太多次都没希望，则直接lock()长期等待
                HashEntry<K,V> f; // to recheck first below
                // 1. retries<0的处理情况
                if (retries < 0) {
                	// 1.1 如果当前的HashEntry节点为空(意味着，在该HashEntry链表上没有找到”要插入的键值对“对应的节点)，而且node=null；则新建HashEntry链表。
                    if (e == null) { // 已到达HashEntry链尾（也可能是该桶位根本没有元素）
                        if (node == null) // speculatively create node
                            node = new HashEntry<K,V>(hash, key, value, null);
                        retries = 0;
                    }
                    // 1.2 如果当前的HashEntry节点是”要插入的键值对在该HashEntry上对应的节点“，则设置retries=0
                    else if (key.equals(e.key)) // 此处为何又不是比较key == e.key，而且不需要比较hash?
                        retries = 0;
                    // 1.3 设置为下一个HashEntry。
                    else
                        e = e.next;
                }
                // 2. 如果自旋次数超过限制，则获取“锁”并退出
                else if (++retries > MAX_SCAN_RETRIES) {
                    lock();
                    break;
                }
                // 3. 当“尝试了偶数次”时，就获取“当前Segment的第一个HashEntry”，即f。
                // 然后，通过f!=first来判断“当前Segment的第一个HashEntry是否发生了改变”。
                // 若是的话，则重置e，first和retries的值，并重新遍历。
                else if ((retries & 1) == 0 &&
                         (f = entryForHash(this, hash)) != first) { // 若在自旋期间，HashEntry链表的表头发生变化；则重新进行查找和自旋工作. 相当于中奖：再来一次
                    e = first = f; // re-traverse if entry changed
                    retries = -1;
                }
            }
            return node;
        }

        /**
         * Scans for a node containing the given key while trying to
         * acquire lock for a remove or replace operation. Upon
         * return, guarantees that lock is held.  Note that we must
         * lock even if the key is not found, to ensure sequential
         * consistency of updates.
         */
        private void scanAndLock(Object key, int hash) {
            // similar to but simpler than scanAndLockForPut
            HashEntry<K,V> first = entryForHash(this, hash);
            HashEntry<K,V> e = first;
            int retries = -1;
            
            // 查找”key-value键值对“在”HashEntry链表上对应的节点“；
            // 无论找没找到，最后都会不断的自旋；在自旋期间，若通过tryLock()获取锁成功则返回；否则自旋MAX_SCAN_RETRIES次数之后，强制获取”锁“并退出。
            // 若在自旋期间，HashEntry链表的表头发生变化；则重新进行查找和自旋！
            while (!tryLock()) {
                HashEntry<K,V> f;
                if (retries < 0) { // 似乎只是改变了自旋等待的时间，还有其他用处吗？
                	// 如果“遍历完该HashEntry链表，仍然没找到”要删除的键值对“对应的节点”
                    // 或者“在该HashEntry链表上找到”要删除的键值对“对应的节点”，则设置retries=0
                    // 否则，设置e为下一个HashEntry节点。
                    if (e == null || key.equals(e.key)) // found it. 已到达HashEntry链尾（也可能是该桶位根本没有元素），或者找到相应的key
                        retries = 0;
                    else
                        e = e.next;
                }
                // 自旋超过限制次数之后，获取锁并退出。
                else if (++retries > MAX_SCAN_RETRIES) {
                    lock();
                    break;
                }
                // 当“尝试了偶数次”时，就获取“当前Segment的第一个HashEntry”，即f。
                // 然后，通过f!=first来判断“当前Segment的第一个HashEntry是否发生了改变”。
                // 若是的话，则重置e，first和retries的值，并重新遍历。
                else if ((retries & 1) == 0 &&
                         (f = entryForHash(this, hash)) != first) { // 相当于中奖：再来一次
                    e = first = f;
                    retries = -1;
                }
            }
        }

        /**
         * Remove; match on key only if value null, else match both.
         */
        final V remove(Object key, int hash, Object value) {
        	// 尝试获取Segment对应的锁。
            // 尝试失败的话，则通过scanAndLock()来获取锁。
            if (!tryLock())
                scanAndLock(key, hash);
            V oldValue = null;
            try {
                HashEntry<K,V>[] tab = table;
                int index = (tab.length - 1) & hash; // HashEntry索引号
                HashEntry<K,V> e = entryAt(tab, index);
                HashEntry<K,V> pred = null;
                // 遍历查找key对应的节点，找到的话将其删除
                while (e != null) {
                    K k;
                    HashEntry<K,V> next = e.next;
                    if ((k = e.key) == key ||
                        (e.hash == hash && key.equals(k))) { // found it
                        V v = e.value;
                        if (value == null || value == v || value.equals(v)) { // check value
                            if (pred == null)
                                setEntryAt(tab, index, next); // set head
                            else
                                pred.setNext(next); // skip current node, pred connect to next
                            ++modCount;
                            --count;
                            oldValue = v;
                        }
                        break;
                    }
                    pred = e;
                    e = next;
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        final boolean replace(K key, int hash, V oldValue, V newValue) {
            if (!tryLock())
                scanAndLock(key, hash);
            boolean replaced = false;
            try {
                HashEntry<K,V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) {
                    K k;
                    if ((k = e.key) == key ||
                        (e.hash == hash && key.equals(k))) {
                        if (oldValue.equals(e.value)) { // 比较旧值
                            e.value = newValue; // 更新
                            ++modCount;
                            replaced = true;
                        }
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return replaced;
        }

        final V replace(K key, int hash, V value) {
            if (!tryLock())
                scanAndLock(key, hash);
            V oldValue = null;
            try {
                HashEntry<K,V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) {
                    K k;
                    if ((k = e.key) == key ||
                        (e.hash == hash && key.equals(k))) { // found it
                        oldValue = e.value; // 旧值
                        e.value = value;
                        ++modCount;
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        final void clear() {
            lock();
            try {
                HashEntry<K,V>[] tab = table;
                // 遍历将每个元素设置为null
                for (int i = 0; i < tab.length ; i++)
                    setEntryAt(tab, i, null);
                ++modCount;
                count = 0; // 个数清0
            } finally {
                unlock();
            }
        }
    }

    // Accessing segments

    /**
     * Gets the jth element of given segment array (if nonnull) with
     * volatile element access semantics via Unsafe. (The null check
     * can trigger harmlessly only during deserialization.) Note:
     * because each element of segments array is set only once (using
     * fully ordered writes), some performance-sensitive methods rely
     * on this method only as a recheck upon null reads.
     */
    @SuppressWarnings("unchecked")
    static final <K,V> Segment<K,V> segmentAt(Segment<K,V>[] ss, int j) {
        long u = (j << SSHIFT) + SBASE;
        return ss == null ? null :
            (Segment<K,V>) UNSAFE.getObjectVolatile(ss, u);
    }

    /**
     * 创建或获取对应索引的Segment分段.
     * 主要步骤：
     * <ol>
     * <li>以ss[0]为原型，创建新的Segment。加载因子、阈值和容量与segments[0]相同
     * <li>自旋CAS保存新的Segment到segments数组中
     * </ol>
     * <p>
     * Returns the segment for the given index, creating it and
     * recording in segment table (via CAS) if not already present.
     *
     * @param k the index
     * @return the segment
     */
    @SuppressWarnings("unchecked")
    private Segment<K,V> ensureSegment(int k) {
        final Segment<K,V>[] ss = this.segments;
        long u = (k << SSHIFT) + SBASE; // raw offset. Segment对应的偏移量
        Segment<K,V> seg;
        
        if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) { // 又检查了一遍
        	/*
        	 * 1.新Segment的加载因子、阈值和容量都以ss[0]为原型进行设置
        	 */
            Segment<K,V> proto = ss[0]; // use segment 0 as prototype
            int cap = proto.table.length; // 新Segment容量
            float lf = proto.loadFactor; // 新Segment加载因子
            int threshold = (int)(cap * lf); // 新Segment阈值
            HashEntry<K,V>[] tab = (HashEntry<K,V>[])new HashEntry[cap]; // 新Segment对应的节点数组
            
            if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                == null) { // recheck. 再次检查
                Segment<K,V> s = new Segment<K,V>(lf, threshold, tab); // 创建新的分段
                // 2.自旋CAS保存新的Segment到segments数组中
                while ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) // 要么其他线程操作成功
                       == null) {
                    if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s)) // 要么CAS成功
                        break;
                }
            }
        }
        return seg;
    }

    // Hash-based segment and entry accesses

    /**
     * 根据哈希值查找Segment
     * <p>
     * Get the segment for the given hash
     */
    @SuppressWarnings("unchecked")
    private Segment<K,V> segmentForHash(int h) {
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        return (Segment<K,V>) UNSAFE.getObjectVolatile(segments, u);
    }

    /**
     * 根据hash值获取指定Segment的第一个HashEntry节点
     * <p>
     * Gets the table entry for the given segment and hash
     */
    @SuppressWarnings("unchecked")
    static final <K,V> HashEntry<K,V> entryForHash(Segment<K,V> seg, int h) {
        HashEntry<K,V>[] tab;
        return (seg == null || (tab = seg.table) == null) ? null :
            (HashEntry<K,V>) UNSAFE.getObjectVolatile
            (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the specified initial
     * capacity, load factor and concurrency level.
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements. 哈希表的初始容量。需要注意的是，哈希表的实际容量=“segments的容量” x “segments中数组的长度”。
     * @param loadFactor  the load factor threshold, used to control resizing.
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold. 加载因子。它是哈希表在其容量自动增加之前可以达到多满的一种尺度。
     * @param concurrencyLevel the estimated number of concurrently
     * updating threads. The implementation performs internal sizing
     * to try to accommodate this many threads. 用来计算segments数组的容量大小。先计算出“大于或等于concurrencyLevel的最小的2的N次方值”，然后将其保存为“segments的容量大小(ssize)”。
     * @throws IllegalArgumentException if the initial capacity is
     * negative or the load factor or concurrencyLevel are
     * nonpositive.
     */
    @SuppressWarnings("unchecked")
    public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        if (concurrencyLevel > MAX_SEGMENTS)
            concurrencyLevel = MAX_SEGMENTS;
        
        // 1.先计算segments数组的大小，必须为2的幂
        // Find power-of-two sizes best matching arguments
        int sshift = 0; // 0~16
        int ssize = 1; // Segment数组大小，有多少个Segment. 1~65536
        // concurrencyLevel的作用就是用来计算segments数组的容量大小。
        // 先计算出“大于或等于concurrencyLevel的最小的2的N次方值”，然后将其保存为“segments的容量大小(ssize)”。
        while (ssize < concurrencyLevel) { // concurrencyLevel最大为65536
            ++sshift; // 最大为16
            ssize <<= 1; // 最大为65536
        }
        // 默认initialCapacity和concurrencyLevel都为16，此时sshift为4，ssize为16
        this.segmentShift = 32 - sshift; // 默认为28. 16~32
        this.segmentMask = ssize - 1; // 默认为15. 0、1、3、7、15、31、63~65535
        
        // 2.再计算出每个Segment的容量，必须为2的幂
        // 哈希表的初始容量
        // 哈希表的实际容量=“segments的容量” x “segments中数组的长度”
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY; // initialCapacity默认为16
        // “哈希表的初始容量” / “segments的容量”
        int c = initialCapacity / ssize; // 每个Segment的容量. 默认为16/16=1
        if (c * ssize < initialCapacity)
            ++c;
        // cap就是“segments中的HashEntry数组的长度”
        int cap = MIN_SEGMENT_TABLE_CAPACITY; // Segment容量最小为2，容量必须为2的幂
        while (cap < c)
            cap <<= 1;
        
        // 3.创建segments数组，并初始化第一个Segment
        // create segments and segments[0]
        Segment<K,V> s0 =
            new Segment<K,V>(loadFactor, (int)(cap * loadFactor),
                             (HashEntry<K,V>[])new HashEntry[cap]); // 创建第一个Segment
        Segment<K,V>[] ss = (Segment<K,V>[])new Segment[ssize]; // 创建Segment数组
        UNSAFE.putOrderedObject(ss, SBASE, s0); // ordered write of segments[0]
        this.segments = ss;
    }

    /**
     * Creates a new, empty map with the specified initial capacity
     * and load factor and with the default concurrencyLevel (16).
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative or the load factor is nonpositive
     *
     * @since 1.6
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with the specified initial capacity,
     * and with default load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative.
     */
    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with a default initial capacity (16),
     * load factor (0.75) and concurrencyLevel (16).
     */
    public ConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new map with the same mappings as the given map.
     * The map is created with a capacity of 1.5 times the number
     * of mappings in the given map or 16 (whichever is greater),
     * and a default load factor (0.75) and concurrencyLevel (16).
     *
     * @param m the map
     */
    public ConcurrentHashMap(Map<? extends K, ? extends V> m) { // 初始化容量=Max(通过输入Map大小计算出容量+1, 默认初始化大小)
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                      DEFAULT_INITIAL_CAPACITY),
             DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
        putAll(m);
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        /*
         * Sum per-segment modCounts to avoid mis-reporting when
         * elements are concurrently added and removed in one segment
         * while checking another, in which case the table was never
         * actually empty at any point. (The sum ensures accuracy up
         * through at least 1<<31 per-segment modifications before
         * recheck.)  Methods size() and containsValue() use similar
         * constructions for stability checks.
         */
        long sum = 0L;
        final Segment<K,V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K,V> seg = segmentAt(segments, j);
            if (seg != null) {
                if (seg.count != 0) // 只要有一个Segment的count不为0，说明有元素，HashMap不为空
                    return false;
                sum += seg.modCount;
            }
        }
        // 如果所有modCount之和不为0，则说明HashMap有过变动，再检查一次
        if (sum != 0L) { // recheck unless no modifications
            for (int j = 0; j < segments.length; ++j) {
                Segment<K,V> seg = segmentAt(segments, j);
                if (seg != null) {
                    if (seg.count != 0)
                        return false;
                    sum -= seg.modCount;
                }
            }
            // 如果前后两次检查的过程中发生过变化，则Map非空
            if (sum != 0L)
                return false;
        }
        return true;
    }

    /**
     * <pre>
     * 最安全的做法，是在统计size的时候把所有Segment的put，remove和clean方法全部锁住，但是这种做法显然非常低效。
     * 因为在累加count操作过程中，之前累加过的count发生变化的几率非常小，
     * 所以ConcurrentHashMap的做法是先尝试2次通过不锁住Segment的方式来统计各个Segment大小，
     * 如果统计的过程中，容器的count发生了变化，则再采用加锁的方式来统计所有Segment的大小。
     * </pre>
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        // Try a few times to get accurate count. On failure due to
        // continuous async changes in table, resort to locking.
        final Segment<K,V>[] segments = this.segments;
        int size;
        boolean overflow; // true if size overflows 32 bits
        long sum;         // sum of modCounts
        long last = 0L;   // previous sum
        int retries = -1; // first iteration isn't retry
        try {
            for (;;) {
            	// 如果统计的过程中，容器的count发生了变化，则再采用加锁的方式来统计所有Segment的大小。
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j)
                        ensureSegment(j).lock(); // force creation
                }
                sum = 0L;
                size = 0;
                overflow = false;
                // 计算所有modCount之和，计算Map的元素数量size
                for (int j = 0; j < segments.length; ++j) {
                    Segment<K,V> seg = segmentAt(segments, j);
                    if (seg != null) {
                        sum += seg.modCount; // modCount之和
                        int c = seg.count; // 当前Segment的元素数量
                        // c < 0或size < 0表示数量溢出了，此处不直接break，是因为需要recheck再决定如何处理
                        if (c < 0 || (size += c) < 0)
                            overflow = true;
                    }
                }
                // 所有modCount之和为0，或者连续两次的总和相等，只退出计算循环
                if (sum == last)
                    break;
                last = sum; // 记录本次计算的所有modCount之和
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j)
                    segmentAt(segments, j).unlock();
            }
        }
        return overflow ? Integer.MAX_VALUE : size;
    }

    /**
     * 返回key在ConcurrentHashMap哈希表中对应的值
     * 
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key.equals(k)},
     * then this method returns {@code v}; otherwise it returns
     * {@code null}.  (There can be at most one such mapping.)
     *
     * @throws NullPointerException if the specified key is null
     */
    public V get(Object key) {
        Segment<K,V> s; // manually integrate access methods to reduce overhead
        HashEntry<K,V>[] tab;
        int h = hash(key);
        /*
         * 默认构造函数：
         * h >>> segmentShift：高4位移至低4位，& segmentMask：获取低4位
         */
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE; // key所在的Segment数组下标
        // 获取key对应的Segment片段。
        // 如果Segment片段不为null，则在“Segment片段的HashEntry数组中”中找到key所对应的HashEntry列表；
        // 接着遍历该HashEntry链表，找到于key-value键值对对应的HashEntry节点。
        if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null && // 获取key所在Segment
            (tab = s.table) != null) {
            for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
                     (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE); // 获取key所在HashEntry
                 e != null; e = e.next) { // 遍历HashEntry
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k))) // found it. 为什么第二种比较之前需要比较hash是否相等?
                    return e.value; // 不需要加锁，因为value为volatile类型，保证了可见性
            }
        }
        return null;
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param  key   possible key
     * @return <tt>true</tt> if and only if the specified object
     *         is a key in this table, as determined by the
     *         <tt>equals</tt> method; <tt>false</tt> otherwise.
     * @throws NullPointerException if the specified key is null
     */
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        Segment<K,V> s; // same as get() except no need for volatile value read
        HashEntry<K,V>[] tab;
        int h = hash(key);
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null &&
            (tab = s.table) != null) {
            for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
                     (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null; e = e.next) {
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than
     * method <tt>containsKey</tt>.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     * @throws NullPointerException if the specified value is null
     */
    public boolean containsValue(Object value) {
        // Same idea as size()
        if (value == null)
            throw new NullPointerException();
        final Segment<K,V>[] segments = this.segments;
        boolean found = false;
        long last = 0;
        int retries = -1;
        try {
            outer: for (;;) {
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j)
                        ensureSegment(j).lock(); // force creation
                }
                long hashSum = 0L;
                int sum = 0;
                // 遍历所有Segment
                for (int j = 0; j < segments.length; ++j) {
                    HashEntry<K,V>[] tab;
                    Segment<K,V> seg = segmentAt(segments, j);
                    if (seg != null && (tab = seg.table) != null) {
                    	// 遍历每个Segment中的所有HashEntry元素
                        for (int i = 0 ; i < tab.length; i++) {
                            HashEntry<K,V> e;
                            for (e = entryAt(tab, i); e != null; e = e.next) {
                                V v = e.value;
                                if (v != null && value.equals(v)) { // found it
                                    found = true;
                                    break outer; // 结束最外层循环
                                }
                            }
                        }
                        sum += seg.modCount; // 计算所有modCount之和
                    }
                }
                // 连续两次的总和相等，只退出循环
                if (retries > 0 && sum == last)
                    break;
                last = sum; // 记录本次计算的所有modCount之和
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j)
                    segmentAt(segments, j).unlock();
            }
        }
        return found;
    }

    /**
     * Legacy method testing if some key maps into the specified value
     * in this table.  This method is identical in functionality to
     * {@link #containsValue}, and exists solely to ensure
     * full compatibility with class {@link java.util.Hashtable},
     * which supported this method prior to introduction of the
     * Java Collections framework.

     * @param  value a value to search for
     * @return <tt>true</tt> if and only if some key maps to the
     *         <tt>value</tt> argument in this table as
     *         determined by the <tt>equals</tt> method;
     *         <tt>false</tt> otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * 添加键值对
     * <p>
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p> The value can be retrieved by calling the <tt>get</tt> method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        Segment<K,V> s;
        if (value == null)
            throw new NullPointerException();
        int hash = hash(key);
        /*
         * 默认构造函数：
         * h >>> segmentShift：高4位移至低4位，& segmentMask：获取低4位
         */
        int j = (hash >>> segmentShift) & segmentMask; // 取高位
        // 如果找不到该Segment，则新建一个。
        if ((s = (Segment<K,V>)UNSAFE.getObject          // nonvolatile; recheck
             (segments, (j << SSHIFT) + SBASE)) == null) //  in ensureSegment
            s = ensureSegment(j); // 创建或获取对应桶位的Segment
        return s.put(key, hash, value, false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V putIfAbsent(K key, V value) {
        Segment<K,V> s;
        if (value == null)
            throw new NullPointerException();
        int hash = hash(key);
        int j = (hash >>> segmentShift) & segmentMask;
        if ((s = (Segment<K,V>)UNSAFE.getObject
             (segments, (j << SSHIFT) + SBASE)) == null)
            s = ensureSegment(j);
        return s.put(key, hash, value, true);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
    	// 遍历输入的Map，将元素逐个通过put方法存入当前HashMap
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * 删除指定key
     * <p>
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param  key the key that needs to be removed
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key is null
     */
    public V remove(Object key) {
        int hash = hash(key);
        Segment<K,V> s = segmentForHash(hash);
        return s == null ? null : s.remove(key, hash, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        int hash = hash(key);
        Segment<K,V> s;
        return value != null && (s = segmentForHash(hash)) != null &&
            s.remove(key, hash, value) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    public boolean replace(K key, V oldValue, V newValue) {
        int hash = hash(key);
        if (oldValue == null || newValue == null)
            throw new NullPointerException();
        Segment<K,V> s = segmentForHash(hash);
        return s != null && s.replace(key, hash, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    public V replace(K key, V value) {
        int hash = hash(key);
        if (value == null)
            throw new NullPointerException();
        Segment<K,V> s = segmentForHash(hash);
        return s == null ? null : s.replace(key, hash, value);
    }

    /**
     * Removes all of the mappings from this map.
     */
    public void clear() {
        final Segment<K,V>[] segments = this.segments;
        // 遍历所有Segment，对每个Segment执行clear()
        for (int j = 0; j < segments.length; ++j) {
            Segment<K,V> s = segmentAt(segments, j);
            if (s != null)
                s.clear();
        }
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from this map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  The collection
     * supports element removal, which removes the corresponding
     * mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt>, and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /* ---------------- Iterator Support -------------- */

    /**
     * 哈希迭代器
     * 
     * @author Administrator
     *
     */
    abstract class HashIterator {
        int nextSegmentIndex;
        int nextTableIndex;
        HashEntry<K,V>[] currentTable;
        HashEntry<K, V> nextEntry;
        HashEntry<K, V> lastReturned;

        HashIterator() {
            nextSegmentIndex = segments.length - 1; // 最后一个Segment的索引号
            nextTableIndex = -1;
            advance();
        }

        /**
         * 找到第一个不为空的Segment的第一个不为空的HashEntry
         * <p>
         * Set nextEntry to first node of next non-empty table
         * (in backwards order, to simplify checks).
         */
        final void advance() {
            for (;;) {
                if (nextTableIndex >= 0) {
                	// 由后往前查找下一个不为null的元素
                    if ((nextEntry = entryAt(currentTable,
                                             nextTableIndex--)) != null)
                        break;
                }
                else if (nextSegmentIndex >= 0) {
                	// 由后往前找到不为空的Segment
                    Segment<K,V> seg = segmentAt(segments, nextSegmentIndex--);
                    if (seg != null && (currentTable = seg.table) != null)
                        nextTableIndex = currentTable.length - 1; // table的最后一个元素索引号
                }
                else
                    break;
            }
        }

        /**
         * 获取下一个元素
         * 
         * @return
         */
        final HashEntry<K,V> nextEntry() {
            HashEntry<K,V> e = nextEntry;
            if (e == null)
                throw new NoSuchElementException();
            lastReturned = e; // cannot assign until after null check
            // 设置nextEntry指向下一个元素
            if ((nextEntry = e.next) == null) // 这里和advance()遍历的方向相反？
                advance();
            return e;
        }

        /**
         * 是否还有元素
         * 
         * @return
         */
        public final boolean hasNext() { return nextEntry != null; }
        public final boolean hasMoreElements() { return nextEntry != null; }

        /**
         * 删除最后一个返回的元素
         */
        public final void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            ConcurrentHashMap.this.remove(lastReturned.key);
            lastReturned = null;
        }
    }

    /**
     * Key迭代器
     * 
     * @author Administrator
     *
     */
    final class KeyIterator
        extends HashIterator
        implements Iterator<K>, Enumeration<K>
    {
        public final K next()        { return super.nextEntry().key; }
        public final K nextElement() { return super.nextEntry().key; }
    }

    /**
     * 值迭代器
     * 
     * @author Administrator
     *
     */
    final class ValueIterator
        extends HashIterator
        implements Iterator<V>, Enumeration<V>
    {
        public final V next()        { return super.nextEntry().value; }
        public final V nextElement() { return super.nextEntry().value; }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays
     * setValue changes to the underlying map.
     */
    final class WriteThroughEntry
        extends AbstractMap.SimpleEntry<K,V>
    {
        WriteThroughEntry(K k, V v) {
            super(k,v);
        }

        /**
         * Set our entry's value and write through to the map. The
         * value to return is somewhat arbitrary here. Since a
         * WriteThroughEntry does not necessarily track asynchronous
         * changes, the most recent "previous" value could be
         * different from what we return (or could even have been
         * removed in which case the put will re-establish). We do not
         * and cannot guarantee more.
         */
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = super.setValue(value);
            ConcurrentHashMap.this.put(getKey(), value);
            return v;
        }
    }

    /**
     * Entry迭代器
     * 
     * @author Administrator
     *
     */
    final class EntryIterator
        extends HashIterator
        implements Iterator<Entry<K,V>>
    {
        public Map.Entry<K,V> next() {
            HashEntry<K,V> e = super.nextEntry();
            return new WriteThroughEntry(e.key, e.value);
        }
    }

    final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return ConcurrentHashMap.this.remove(o) != null;
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsValue(o);
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            V v = ConcurrentHashMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            return ConcurrentHashMap.this.remove(e.getKey(), e.getValue());
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Save the state of the <tt>ConcurrentHashMap</tt> instance to a
     * stream (i.e., serialize it).
     * @param s the stream
     * @serialData
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        // force all segments for serialization compatibility
        for (int k = 0; k < segments.length; ++k)
            ensureSegment(k);
        s.defaultWriteObject();

        final Segment<K,V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segmentAt(segments, k);
            seg.lock();
            try {
                HashEntry<K,V>[] tab = seg.table;
                // 将Segment中的所有元素写到输出流
                for (int i = 0; i < tab.length; ++i) {
                    HashEntry<K,V> e;
                    for (e = entryAt(tab, i); e != null; e = e.next) {
                        s.writeObject(e.key);
                        s.writeObject(e.value);
                    }
                }
            } finally {
                seg.unlock();
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitute the <tt>ConcurrentHashMap</tt> instance from a
     * stream (i.e., deserialize it).
     * @param s the stream
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        // Don't call defaultReadObject()
        ObjectInputStream.GetField oisFields = s.readFields();
        final Segment<K,V>[] oisSegments = (Segment<K,V>[])oisFields.get("segments", null); // 恢复segments

        final int ssize = oisSegments.length; // segments数组长度
        if (ssize < 1 || ssize > MAX_SEGMENTS
            || (ssize & (ssize-1)) != 0 )  // ssize not power of two
            throw new java.io.InvalidObjectException("Bad number of segments:"
                                                     + ssize);
        int sshift = 0, ssizeTmp = ssize;
        while (ssizeTmp > 1) {
            ++sshift;
            ssizeTmp >>>= 1;
        }
        UNSAFE.putIntVolatile(this, SEGSHIFT_OFFSET, 32 - sshift); // segmentShift
        UNSAFE.putIntVolatile(this, SEGMASK_OFFSET, ssize - 1); // segmentMask
        UNSAFE.putObjectVolatile(this, SEGMENTS_OFFSET, oisSegments); // segments

        // set hashMask
        UNSAFE.putIntVolatile(this, HASHSEED_OFFSET, randomHashSeed(this)); // hashSeed

        // Re-initialize segments to be minimally sized, and let grow.
        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        final Segment<K,V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segments[k];
            if (seg != null) {
                seg.threshold = (int)(cap * seg.loadFactor);
                seg.table = (HashEntry<K,V>[]) new HashEntry[cap];
            }
        }

        // 从输入流读出所有key-value对并写入Map
        // Read the keys and values, and put the mappings in the table
        for (;;) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    /**
     * Segment起始位置偏移量
     */
    private static final long SBASE;
    private static final int SSHIFT;
    /**
     * HashEntry起始位置偏移量
     */
    private static final long TBASE;
    private static final int TSHIFT;
    private static final long HASHSEED_OFFSET;
    private static final long SEGSHIFT_OFFSET;
    private static final long SEGMASK_OFFSET;
    private static final long SEGMENTS_OFFSET;

    static {
        int ss, ts;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class tc = HashEntry[].class;
            Class sc = Segment[].class;
            TBASE = UNSAFE.arrayBaseOffset(tc);
            SBASE = UNSAFE.arrayBaseOffset(sc);
            ts = UNSAFE.arrayIndexScale(tc);
            ss = UNSAFE.arrayIndexScale(sc);
            HASHSEED_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("hashSeed"));
            SEGSHIFT_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("segmentShift"));
            SEGMASK_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("segmentMask"));
            SEGMENTS_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("segments"));
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((ss & (ss-1)) != 0 || (ts & (ts-1)) != 0)
            throw new Error("data type scale not a power of two");
        SSHIFT = 31 - Integer.numberOfLeadingZeros(ss);
        TSHIFT = 31 - Integer.numberOfLeadingZeros(ts);
    }

}
