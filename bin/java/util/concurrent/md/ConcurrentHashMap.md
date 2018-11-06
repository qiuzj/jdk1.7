
## hash

**Segment[]数组下标取hash高位**

int j = (hash >>> segmentShift) & segmentMask;  
(j << SSHIFT) + SBASE) ?

**HashEntry[]数组下标取hash低位**

int index = (tab.length - 1) & hash;  
((long)i << TSHIFT) + TBASE ?

**put方法**

计算key所在Segment数组的offset  

获取 Segment

如果找不到该Segment，则新建一个。  
创建或获取对应索引的Segment分段. 主要步骤：  
- 以ss[0]为原型，创建新的Segment。加载因子、阈值和容量与segments[0]相同   
- 自旋CAS保存新的Segment到segments数组中   

将键值对存入Segment的HashEntry[]

- tryLock()获取锁  
    - 获取失败则通过scanAndLockForPut自旋获取锁，并做点初始化工作
- 获得锁之后，计算HashEntry[]数组offset
- 获取位桶的第一个节点HashEntry<K,V> first = entryAt(tab, index);
- 遍历链表
    - 如果未达链尾，则比较key是否存在
        - 如果存在，则保存旧值，如果!onlyIfAbsent则更新值
        - 如果不存在，则获取下一个节点
    - 如果到达链尾，仍找不到节点
        - 则创建节点并设置为首节点
        - 如果添加key-value键值对之后，Segment中的元素超过阈值(并且，HashEntry数组的长度没超过限制)，则rehash；

**get方法**

计算key所在的Segment数组offset

如果Segment存在，并且HashEntry[]存在

- 遍历HashEntry[]查找节点


