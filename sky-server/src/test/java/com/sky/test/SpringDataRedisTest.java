package com.sky.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Spring Data Redis 操作五种数据类型 + 通用命令的测试类（对应讲义 4.2.2 / 4.2.3）
 *
 * 每个方法演示一种数据类型，注释里都标了对应的 Redis 原生命令。
 * 注意：所有测试方法共用同一个 Redis 服务（application-dev.yml 里配的 database 10），
 * 先跑前面的方法写入数据，最后 testCommon 里的 keys("*") 才能查到它们。
 *
 * 备注：RedisConfiguration 里只把 key 换成了 String 序列化器，value 仍是默认的 JDK 序列化器，
 * 所以在 Another Redis Desktop Manager 里看 value 会是带 \xac\xed 前缀的"乱码"，
 * 属正常现象，Java 代码里读写没问题（后面课程会讲到 value 序列化器的设置）。
 */
@SpringBootTest
public class SpringDataRedisTest {
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 环境验证：确认 RedisTemplate 注入成功，并能拿到 5 种数据类型各自的操作对象
     */
    @Test
    public void testRedisTemplate(){
        System.out.println(redisTemplate);
        //string数据操作
        ValueOperations valueOperations = redisTemplate.opsForValue();
        //hash类型的数据操作
        HashOperations hashOperations = redisTemplate.opsForHash();
        //list类型的数据操作
        ListOperations listOperations = redisTemplate.opsForList();
        //set类型的数据操作
        SetOperations setOperations = redisTemplate.opsForSet();
        //zset类型的数据操作
        ZSetOperations zSetOperations = redisTemplate.opsForZSet();
    }

    /**
     * 操作字符串类型的数据
     * string：Redis 最简单的类型，一个 key 对应一个 value
     */
    @Test
    public void testString(){
        // 本方法演示 4 个命令：set get setex setnx
        // set("name","小明") 等价于 SET name 小明 —— 不过期，永久有效
        redisTemplate.opsForValue().set("name","小明");

        // get("name") 等价于 GET name；RedisTemplate 没写泛型，返回 Object，需强转 String
        // 课件变量名叫 city 是历史遗留命名，实际取到的是 key=name 的值"小明"
        String city = (String) redisTemplate.opsForValue().get("name");
        System.out.println(city);   // 控制台输出：小明

        // set 带 3 个参数 等价于 SETEX code 180 1234
        // 3 = 过期时间的数值，TimeUnit.MINUTES = 时间单位是分钟 → 3 分钟后 Redis 自动删除该 key
        // 典型场景：手机验证码（有效期内可校验，过期自动作废，不用手动清理）
        redisTemplate.opsForValue().set("code","1234",3, TimeUnit.MINUTES);

        // setIfAbsent 等价于 SETNX lock 1（set if not exist）：只在 key 不存在时才设置
        // 此刻 lock 不存在 → 设置成功，返回 true
        redisTemplate.opsForValue().setIfAbsent("lock","1");

        // lock 上一步已经存在 → 本次不覆盖（value 仍是"1"），返回 false
        // 这个"只有第一次能成功"的特性，就是后面分布式锁章节要用的原理
        redisTemplate.opsForValue().setIfAbsent("lock","2");
    }

    /**
     * 操作哈希类型的数据
     * hash：key 下面再分 field-value 两级结构，类似 Java 的 HashMap<String,String>
     * 适合存对象：key=对象 id，field=属性名，value=属性值
     */
    @Test
    public void testHash(){
        //hset hget hdel hkeys hvals
        // 拿到操作 hash 类型的对象，后面所有 hash 操作都通过它调（对应 HSET/HGET 等命令）
        HashOperations hashOperations = redisTemplate.opsForHash();

        // put(k, field, v) 等价于 HSET 100 name tom —— 给 key=100 的哈希表加字段 name=值 tom
        hashOperations.put("100","name","tom");
        // 再加一个字段 age=20，此时 key=100 的结构：{name=tom, age=20}
        hashOperations.put("100","age","20");

        // get(k, field) 等价于 HGET 100 name —— 只取哈希表里 name 这一个字段的值，需强转
        String name = (String) hashOperations.get("100", "name");
        System.out.println(name);   // 输出：tom

        // keys(k) 等价于 HKEYS 100 —— 取哈希表所有字段名（注意：是 field 不是 value）
        Set keys = hashOperations.keys("100");
        System.out.println(keys);   // 输出：[name, age]

        // values(k) 等价于 HVALS 100 —— 取哈希表所有字段的值
        List values = hashOperations.values("100");
        System.out.println(values); // 输出：[tom, 20]（顺序不保证）

        // delete(k, field...) 等价于 HDEL 100 age —— 删的是字段 age；key=100 本身还在（剩 name）
        hashOperations.delete("100","age");
    }

    /**
     * 操作列表类型的数据
     * list：双向链表（类似 Java 的 LinkedList），按插入顺序排、可重复
     * 头部进 leftPush + 尾部出 rightPop = 经典队列；头部进头部出 = 栈
     */
    @Test
    public void testList(){
        //lpush lrange rpop llen
        ListOperations listOperations = redisTemplate.opsForList();

        // leftPushAll 等价于 LPUSH mylist a b c —— 把 a、b、c 依次从头部插入
        // 关键理解：LPUSH 是逐个往头部塞，后插的反而排最前
        //   插 a → [a]；插 b → [b, a]；插 c → [c, b, a]
        listOperations.leftPushAll("mylist","a","b","c");
        // leftPush 等价于 LPUSH mylist d —— 再插一个 d 到头部 → [d, c, b, a]
        listOperations.leftPush("mylist","d");

        // range(k, start, stop) 等价于 LRANGE mylist 0 -1 —— 取下标 0 到末尾的全部元素
        // -1 是固定用法：代表最后一个元素的下标，所以 0 ~ -1 就是"整个列表"
        List mylist = listOperations.range("mylist", 0, -1);
        System.out.println(mylist); // 输出：[d, c, b, a]

        // rightPop 等价于 RPOP mylist —— 从尾部移除并返回最后一个元素（即 a）
        listOperations.rightPop("mylist");  // 执行后列表变成 [d, c, b]

        // size 等价于 LLEN mylist —— 列表长度
        Long size = listOperations.size("mylist");
        System.out.println(size);   // 输出：3
    }

    /**
     * 操作集合类型的数据
     * set：无序、成员唯一不重复（类似 Java 的 HashSet），擅长去重和交并集运算
     */
    @Test
    public void testSet(){
        //sadd smembers scard sinter sunion srem
        SetOperations setOperations = redisTemplate.opsForSet();

        // add 等价于 SADD set1 a b c d —— 一次添加 4 个成员
        setOperations.add("set1","a","b","c","d");
        setOperations.add("set2","a","b","x","y");

        // members 等价于 SMEMBERS set1 —— 返回所有成员（无序，每次打印顺序可能不同）
        Set members = setOperations.members("set1");
        System.out.println(members);    // 输出：[a, b, c, d]（顺序不定）

        // size 等价于 SCARD set1 —— 集合成员个数
        Long size = setOperations.size("set1");
        System.out.println(size);   // 输出：4

        // intersect 等价于 SINTER set1 set2 —— 交集：两个集合都有的元素
        Set intersect = setOperations.intersect("set1", "set2");
        System.out.println(intersect);  // 输出：[a, b]

        // union 等价于 SUNION set1 set2 —— 并集：合并两个集合并去重后的全部元素
        Set union = setOperations.union("set1", "set2");
        System.out.println(union);  // 输出：[a, b, c, d, x, y]

        // remove 等价于 SREM set1 a b —— 移除指定成员，set1 剩 [c, d]
        setOperations.remove("set1","a","b");
    }

    /**
     * 操作有序集合类型的数据
     * zset（sorted set）：每个成员关联一个 double 分数 score，按分数升序排列、成员不重复
     * 典型场景：排行榜（点赞数、销量排名）
     */
    @Test
    public void testZset(){
        //zadd zrange zincrby zrem
        ZSetOperations zSetOperations = redisTemplate.opsForZSet();

        // add(k, member, score) 等价于 ZADD zset1 10 a —— 添加成员 a，分数 10
        zSetOperations.add("zset1","a",10);
        zSetOperations.add("zset1","b",12);
        zSetOperations.add("zset1","c",9);  // 此时三个成员：a=10, b=12, c=9

        // range 等价于 ZRANGE zset1 0 -1 —— 按分数从低到高返回全部成员
        Set zset1 = zSetOperations.range("zset1", 0, -1);
        System.out.println(zset1);  // 输出：[c, a, b]（按分数 9<10<12 排，不是按插入顺序）

        // incrementScore 等价于 ZINCRBY zset1 10 c —— 成员 c 的分数 +10，变成 19
        // 这就是排行榜"加分"的实现；此后排序变为 a=10 < b=12 < c=19
        zSetOperations.incrementScore("zset1","c",10);

        // remove 等价于 ZREM zset1 a b —— 删除成员 a 和 b，zset1 剩 c(19)
        zSetOperations.remove("zset1","a","b");
    }

    /**
     * 通用命令操作
     * 这些命令不分数据类型，对任何 key 都能用
     */
    @Test
    public void testCommon(){
        //keys exists type del
        // keys("*") 等价于 KEYS * —— "*" 是通配符，匹配所有 key
        // 能查到前面 5 个方法留下的所有 key（name/code/lock/100/mylist/set1/set2/zset1 等）
        // 注意：key 数量巨大时 KEYS * 会阻塞 Redis，生产环境禁用，这里仅学习用
        Set keys = redisTemplate.keys("*");
        System.out.println(keys);

        // hasKey 等价于 EXISTS <key> —— 判断 key 是否存在，返回 Boolean
        Boolean name = redisTemplate.hasKey("name");    // 前面存过 → true
        Boolean set1 = redisTemplate.hasKey("set1");    // 前面存过 → true
        // 课件只演示用法，这两个变量没打印，IDEA 会有黄色"未使用"警告，无碍

        // 遍历查到的所有 key，逐个查询数据类型
        for (Object key : keys) {
            // type(key) 等价于 TYPE <key> —— 返回枚举 DataType，name() 打印类型名：
            // STRING / HASH / LIST / SET / ZSET
            DataType type = redisTemplate.type(key);
            System.out.println(type.name());
        }

        // delete 等价于 DEL mylist —— 删除整个 key（整个列表一起删，不是删单个元素）
        redisTemplate.delete("mylist");
    }
}
