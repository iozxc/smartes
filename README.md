## mybatis-plus整合elasticsearch7.14。且能够自动的增删改（还可定制）elasticsearch的index数据，以及自动创建index。还有简化版的es的搜索功能，达到基本的单keyword查询功能

### maven

```xml

<dependency>
    <groupId>cn.omisheep</groupId>
    <artifactId>smartes-spring-boot-starter</artifactId>
    <version>0.0.5</version>
</dependency>
```

```yml
smartes:
  client:
    hostname: # 服务器ip
    port: 9200
    scheme: http
    username: # 用户名
    password: # 密码
  service:
    pre-tags: <span class="highlight">
    post-tags: </span>
    pkg: # 实体类所在包名
```

- 版本使用7.14.0

### 1. 自动创建索引

如下，有三个注解，分别是`@Index`，`@IndexId`，`@IndexField`。

```java

@Data
@Index
@TableName("article")
@Accessors(chain = true)
public class Article {

    @TableId(value = "id", type = IdType.AUTO)
    @IndexId
    private Integer id;

    @IndexField(isSearch = false)
    private String userAvatar;

    @IndexField
    private String type;
    @IndexField
    private String tags;

    @IndexField
    private String title;
    @IndexField(searchAnalyzer = "ik_smart")
    private String content;
    @IndexField(isSearch = false)
    private String firstPicture;
    @IndexField
    private String description;

    @IndexField
    private Integer views; // 浏览量
    @IndexField
    @TableField(value = "`like`")
    private Integer like; // 浏览量

    @IndexField
    private Date createTime;

}
```

- `@Index`

  该注解用于实体类上面，被注解标记的实体类在springboot运行时会自动判断你的服务器有没有对应的索引，如果没有会创建，有的话会update


- `@IndexId`

  该注解用于属性上，被该注解标记的实体类在创建index时或者数据自动增删改时会将其当作es的id，什么类型无所谓，类型会自动转成string。


- `@IndxField`

  该注解用于属性上，被该注解标记的实体类顾名思义就是index里面的属性。可以自定义**搜索方式**，**分词方式**，**指定类型**，**
  指定名称**
  ，基础类型可以不写，其他的，比如String类型可以有text或者keyword。默认text。

### 2. 增删改统一方法

如下

```java
public interface ElasticSearchService {
    boolean isIndex(Class<?> clz);

    boolean createIndex(Class<?> clz);

    boolean isExistIndex(Class<?> clz);

    boolean isAllExistIndex();

    boolean updateIndex(Class<?> clz);

    <E> int insert(E o);

    <E> boolean insert(List<E> list);

    <E> int update(E o);

    <E> boolean update(List<E> list);

    int delete(String clzName,
               String id);

    boolean delete(String clzName,
                   List<String> list);

    HashMap<String, List<Object>> search(String keyword,
                                         Integer pageNo,
                                         Integer pageSize,
                                         String type,
                                         String index,
                                         String must,
                                         String should,
                                         Boolean isHighlight,
                                         Map<String, Object> matchQuery);

    HashMap<String, List<Object>> list(Integer pageNo,
                                       Integer pageSize,
                                       String index,
                                       String sortName,
                                       String order,
                                       Map<String, Object> matchQuery);
}
```

### 3. 搜索查询

```java

import java.util.HashMap;

@RestController
@RequestMapping
@Slf4j
public class SearchController {

    @Autowired
    private ElasticSearchService elasticSearchService;

    @GetMapping(value = {"/search", "/s"})
    public Result search(@RequestParam(value = "kw", required = false) String keyword,
                         @RequestParam(required = false) Integer pageNo,
                         @RequestParam(required = false) Integer pageSize,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String index,
                         @RequestParam(required = false) String must,
                         @RequestParam(required = false) String should,
                         @RequestParam(value = "highlight", required = false, defaultValue = "true") Boolean isHighlight) {
        if (keyword == null) {
            return Result.SUCCESS.data();
        }
        HashMap<String, Boolean> map = new HashMap<>();
        map.put("published", true);
        HashMap<String, List<Object>> search = elasticSearchService.search(keyword, pageNo, pageSize, type, index, must,
                                                                           should, isHighlight, map);
        return Result.SUCCESS.data(search);
    }


    @GetMapping(value = {"/list", "/l"})
    public Result list(@RequestParam(value = "i", required = false) String index,
                       @RequestParam(required = false) Integer pageNo,
                       @RequestParam(required = false) Integer pageSize,
                       @RequestParam(required = false) String sortName,
                       @RequestParam(required = false) String order) {
        if (index == null) {
            return Result.SUCCESS.data();
        }
        HashMap<String, List<Object>> list = elasticSearchService.list(pageNo, pageSize, index, sortName, order, null);
        return Result.SUCCESS.data(list);
    }


}
```

- **search**方法。根据关键字，在指定的index里面搜索，支持分页，可以指定搜索的字段，以及是must还是should。同时是否要高亮显示。

  ```java
  public HashMap<String, List<Object>> search(String keyword, 
                                              Integer pageNo,
                                              Integer pageSize,
                                              String type,
                                              String index,
                                              String must,
                                              String should,
                                              Boolean isHighlight){...}
  ```

  > `index` 这个字段可以写成如  **user,blog,comment** 这种格式用逗号隔开。must，should同理。
  >
  > `type` 为只有在must和should都为null时才生效，默认把指定index的指定类型的所有内容全部查询出来。

- **list** 方法。得到一个排序的集合，支持分页。

  ```java
  public HashMap<String, List<Object>> list(Integer pageNo,
                                            Integer pageSize,
                                            String index,
                                            String sortName,
                                            String order) 
  ```

  > `index` 指定的indx名，用法和search一样，可以多个。
  >
  > `sortName` 排序的字段名，可以为空，使用默认排序
  >
  > `order` 排序方式 ASC 或者 DESC

以上两个方法的返回值格式如图

```json
{
  "article": [
    {
      "createTime": "xx-xx-xx xx:xx:xx",
      "like": 123123,
      "userAvatar": "",
      "description": "nihaoa ",
      "id": "21",
      "content": "zxc",
      "views": 999,
      "tags": "<span class=\"highlight\">love</span>"
    },
    {
      "createTime": "xx-xx-xx xx:xx:xx",
      "like": 123123,
      "userAvatar": "",
      "description": "nihaoa ",
      "id": "29",
      "content": "zxc",
      "views": 999,
      "tags": "<span class=\"highlight\">love</span>"
    }
  ],
  "user": [
    {
      ...
    }
  ],
  "xxx": [
    {
      ...
    }
  ]
}
```

