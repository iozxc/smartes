package cn.omisheep.smartes.core;

import cn.omisheep.commons.util.ClassUtils;
import cn.omisheep.smartes.annotation.Index;
import cn.omisheep.smartes.annotation.IndexField;
import cn.omisheep.smartes.annotation.IndexId;
import cn.omisheep.smartes.core.util.Utils;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ElasticSearchServiceImpl implements ElasticSearchService {


    private final        SmartESProperties.ServiceConfig        config;
    /**
     * 系统属性
     */
    private final        RestHighLevelClient                    client;
    /**
     * 时间类型
     */
    private final        String                                 dateFormat    = "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"; // 搜索时的空字段
    private              SimpleDateFormat                       simpleDateFormat;
    /**
     * 类型转换
     */
    private static final String[]                               javaTypes     = {"String", "Integer", "Double", "Float", "Boolean", "Date", "*"};
    private static final String[]                               esTypes       = {"text", "integer", "double", "float", "boolean", "date", "unknown"};
    /**
     * className ==> indexName
     */
    private static final Map<String, String>                    indexMap      = new HashMap<>();
    /**
     * indexName ==> indexId
     */
    private static final Map<String, Field>                     indexIdMap    = new HashMap<>();
    /**
     * indexName ==> filedMap
     */
    private static final Map<String, Map<String, Field>>        indexFiledMap = new HashMap<>();
    /**
     * 所有的index
     */
    private static       String[]                               allIndex;
    /**
     * indexName =>   {text=>[]}
     */
    private static final Map<String, Map<String, List<String>>> typeFieldMix  = new HashMap<>();

    public ElasticSearchServiceImpl(RestHighLevelClient client, SmartESProperties esConfig) {
        this.client = client;
        this.config = esConfig.getService();
        initIndex();
        log.info("init ElasticSearchService successfully");
    }

    private String parseType(Field field) {
        IndexField indexField = field.getAnnotation(IndexField.class);
        if (indexField.type().equals("")) {
            for (int i = 0; i < javaTypes.length; i++) {
                if (javaTypes[i].equals(field.getType().getSimpleName())) {
                    return esTypes[i];
                }
            }
        } else {
            return indexField.type();
        }
        return "unknown";
    }

    private HashMap<String, Object> parseClassToMapping(Class<?> clz) throws Exception {
        Map<String, Field> fieldMap = indexFiledMap.get(indexMap.get(clz.getName()));
        if (fieldMap == null) return null;
        HashMap<String, Object> createIndexMap = new HashMap<>();
        HashMap<String, Object> properties     = new HashMap<>();

        for (Map.Entry<String, Field> entry : fieldMap.entrySet()) {
            HashMap<String, String> info       = new HashMap<>();
            Field                   field      = entry.getValue();
            IndexField              indexField = field.getAnnotation(IndexField.class);
            String                  type       = parseType(field);
            info.put("type", type);
            if (type.equals("unknown")) {
                throw new Exception("elasticsearch类型异常");
            } else if (type.equals("text")) {
                info.put("analyzer", indexField.analyzer());
                info.put("search_analyzer", indexField.searchAnalyzer());
            } else if (type.equals("date")) {
                info.put("format", dateFormat);
            }
            properties.put(entry.getKey(), info);
        }
        createIndexMap.put("properties", properties);
        return createIndexMap;
    }

    @Override
    public boolean isIndex(Class<?> clz) {
        Index index = clz.getDeclaredAnnotation(Index.class);
        return index != null;
    }

    @SneakyThrows
    @Override
    public boolean createIndex(Class<?> clz) {
        if (!isIndex(clz)) {
            log.error("create index error , class: {}  . require a annotation @Index", clz.getName());
            return false;
        }
        HashMap<String, Object> createIndexMap = parseClassToMapping(clz);

        String indexName = indexMap.get(clz.getName());
        log.debug("createIndexMap . index: {} class: {} =>  {}", indexName, clz.getName(), createIndexMap);

        CreateIndexRequest request = new CreateIndexRequest(indexName)
                .mapping(createIndexMap);
        boolean acknowledged = client.indices()
                .create(request, RequestOptions.DEFAULT).isAcknowledged();
        if (acknowledged) {
            log.info("create index {} is successfully", indexName);
        } else {
            log.error("create index {} is fail", indexName);
        }
        return acknowledged;
    }

    @Override
    public boolean isExistIndex(Class<?> clz) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexMap.get(clz.getName()));
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    @Override
    public boolean isAllExistIndex() throws IOException {
        GetIndexRequest request = new GetIndexRequest(
                indexMap.keySet().toArray(ConstantPool.EMPTY_STRINGS)
        );
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    /**
     * 只能增删mapping，不能修改
     *
     * @param clz 对应的index实体类
     */
    @Override
    public boolean updateIndex(Class<?> clz) throws Exception {
        if (!isIndex(clz)) {
            throw new Exception("不是index");
        }
        PutMappingRequest request = new PutMappingRequest(indexMap.get(clz.getName()));

        HashMap<String, Object> mapping = parseClassToMapping(clz);
        request.source(mapping);
        log.info("{} try update index => {}", indexMap.get(clz.getName()), mapping);

        try {
            return client.indices().putMapping(request, RequestOptions.DEFAULT).isAcknowledged();
        } catch (IOException e) {
            log.error("不能修改类型,请删除索引重新添加数据");
            throw e;
        }
    }

    /**
     * 请在save之后调用此方法
     */
    @SneakyThrows
    @Override
    public <E> int insert(E o) {
        log.info(" elastic service insert {}", o);
        Meta meta = new Meta(o);

        IndexRequest request = new IndexRequest(meta.getIndexName())
                .id(meta.getIndexId())
                .timeout("3s")
                .source(meta.getSource());
        return client.index(request, RequestOptions.DEFAULT).status().getStatus();
    }

    @SneakyThrows
    @Override
    public <E> boolean insert(List<E> list) {
        log.info(" elastic service insert list: {}", list);
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("10s");
        for (E o : list) {
            Meta meta = new Meta(o);

            bulkRequest.add(
                    new IndexRequest(meta.getIndexName())
                            .id(meta.getIndexId())
                            .source(meta.getSource())
            );
        }
        BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        log.info("insert a list => {}", list);
        return bulk.hasFailures();
    }

    @SneakyThrows
    @Override
    public <E> int update(E o) {
        log.info(" elastic service update ");
        Meta meta = new Meta(o);

        UpdateRequest request = new UpdateRequest(
                meta.getIndexName(), meta.getIndexId())
                .doc(meta.getSource());

        return client.update(request, RequestOptions.DEFAULT).status().getStatus();
    }

    @SneakyThrows
    @Override
    public <E> boolean update(List<E> list) {
        log.info(" elastic service update list {}", list);
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("10s");

        for (E o : list) {
            Meta meta = new Meta(o);

            bulkRequest.add(
                    new UpdateRequest(
                            meta.getIndexName(), meta.getIndexId())
                            .doc(meta.getSource())
            );
        }
        BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        return bulk.hasFailures();
    }

    @SneakyThrows
    @Override
    public int delete(String clzName, String id) {
        log.info(" elastic service delete ");
        DeleteRequest request = new DeleteRequest(
                indexMap.get(clzName), id)
                .timeout("2s");

        return client.delete(request, RequestOptions.DEFAULT).status().getStatus();
    }

    @SneakyThrows
    @Override
    public boolean delete(String clzName, List<String> list) {
        log.info(" elastic service delete ");
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("10s");
        String indexName = indexMap.get(clzName);
        for (String id : list) {
            bulkRequest.add(
                    new DeleteRequest(indexName, id).timeout("2s")
            );
        }
        BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        return bulk.hasFailures();
    }

    private String[] sp(String str) {
        if (str == null) {
            return ConstantPool.EMPTY_STRINGS;
        } else {
            return str.split(",");
        }
    }

    /**
     * 该搜索搜索
     *
     * @param pageNo   开始页码
     * @param pageSize 页大小
     * @param keyword  不为空  !!!must!!!
     * @param index    索引列表
     * @param must     字段列表：必须匹配
     * @param should   字段列表：应匹配
     * @param type     默认为 text
     * @return HashMap<String, List < Object>> indexName -> 该index下的所有的搜索结果
     */
    @Override
    public HashMap<String, List<Object>> search(String keyword, Integer pageNo, Integer pageSize, String type,
                                                String index, String must, String should, Boolean isHighlight) {
        return searchBuild(
                keyword,
                pageNo == null ? 0 : pageNo,
                pageSize == null ? config.getDefaultPageSize() : pageSize,
                type == null ? "text" : type,
                sp(index),
                sp(must),
                sp(should),
                isHighlight
        );
    }

    @SneakyThrows
    @SuppressWarnings("all")
    private HashMap<String, List<Object>> searchBuild(String keyword, int pageNo, int pageSize, String type,
                                                      String[] indices, String[] must, String[] should, Boolean isHighlight) {
        log.info("pageNo: {} | pageSize: {} | keyword: {} | indices: {} | must: {} | should: {} | type: {}",
                pageNo, pageSize, keyword, indices, must, should, type);
        if (keyword == null || keyword.trim().equals("")) return ConstantPool.EMPTY_HASH_MAP;

        if (indices.length == 0) {
            indices = allIndex;
        } else {
            indices = Arrays.stream(indices)
                    .filter(v1 -> Arrays.asList(allIndex).contains(v1))
                    .toArray(String[]::new);
            if (indices.length == 0) {
                return ConstantPool.EMPTY_HASH_MAP;
            }
        }

        SearchRequest       request             = new SearchRequest(indices);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        HighlightBuilder    highlightBuilder    = new HighlightBuilder();
        BoolQueryBuilder    query               = QueryBuilders.boolQuery();


        if (0 == must.length && 0 == should.length) {
            HashSet<String> set = new HashSet<>();
            for (String index : indices) {
                typeFieldMix.get(index).get(type).forEach(name -> {
                    set.add(name);
                });
            }
            set.forEach(name -> {
                query.should(QueryBuilders.matchQuery(name, keyword));
                highlightBuilder.field(name);
            });
        } else {
            for (String name : must) {
                query.must(QueryBuilders.matchQuery(name, keyword));
                highlightBuilder.field(name);
            }
            for (String name : should) {
                query.should(QueryBuilders.matchQuery(name, keyword));
                highlightBuilder.field(name);
            }
        }
        query.should(QueryBuilders.matchQuery("published", true));

        if (isHighlight != null && isHighlight) {
            highlightBuilder.preTags(config.getPreTags());
            highlightBuilder.postTags(config.getPostTags());
            searchSourceBuilder.highlighter(highlightBuilder);
        }

        SearchRequest searchRequest = request.source(
                searchSourceBuilder
                        .query(query)
                        .from(pageNo)
                        .size(pageSize)
                        .timeout(new TimeValue(60, TimeUnit.SECONDS))
        );

        // 并且返回格式化数据，score排序
        SearchHit[]         hits     = client.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits();
        Iterator<SearchHit> iterator = Arrays.stream(hits).filter(h -> h.getHighlightFields().size() > 0).iterator();

        HashMap<String, List<Object>> dataMap = new HashMap<>();

        for (String index : indices) {
            dataMap.put(index, new ArrayList<>());
        }

        if (isHighlight != null && isHighlight) {
            while (iterator.hasNext()) {
                SearchHit           hit         = iterator.next();
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                for (Map.Entry<String, HighlightField> entry : hit.getHighlightFields().entrySet()) {
                    sourceAsMap.put(entry.getKey(), entry.getValue().fragments()[0].toString());
                }
                sourceAsMap.put("id", hit.getId());
                dataMap.get(hit.getIndex()).add(sourceAsMap);
            }
        } else {
            while (iterator.hasNext()) {
                SearchHit           hit         = iterator.next();
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                sourceAsMap.put("id", hit.getId());
                dataMap.get(hit.getIndex()).add(sourceAsMap);
            }
        }

        dataMap.keySet().removeIf(key -> dataMap.get(key).size() == 0);
        return dataMap;
    }

    @Override
    public HashMap<String, List<Object>> list(Integer pageNo, Integer pageSize, String index, String sortName, String order, boolean isAdmin) {
        return listBuild(
                pageNo == null ? 0 : pageNo,
                pageSize == null ? config.getDefaultPageSize() : pageSize,
                sp(index),
                sortName,
                "asc".equalsIgnoreCase(order) ? SortOrder.ASC : SortOrder.DESC,
                isAdmin
        );
    }

    @SneakyThrows
    @SuppressWarnings("all")
    private HashMap<String, List<Object>> listBuild(int pageNo, int pageSize, String[] indices, String sortName, SortOrder order, boolean isAdmin) {
        log.info("list  -> pageNo: {} | pageSize: {} | indices: {}", pageNo, pageSize, indices);

        if (indices.length == 0) {
            indices = allIndex;
        } else {
            indices = Arrays.stream(indices)
                    .filter(v1 -> Arrays.asList(allIndex).contains(v1))
                    .toArray(String[]::new);
            if (indices.length == 0) {
                return ConstantPool.EMPTY_HASH_MAP;
            }
        }

        SearchRequest       request             = new SearchRequest(indices);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        if (sortName != null) {
            if (order != null) {
                searchSourceBuilder.sort(sortName, order);
            } else {
                searchSourceBuilder.sort(sortName, SortOrder.DESC);
            }
        }

        MatchAllQueryBuilder matchAllQueryBuilder = QueryBuilders.matchAllQuery();

        SearchRequest searchRequest = request.source(
                searchSourceBuilder
                        .query(matchAllQueryBuilder)
                        .from(pageNo)
                        .size(pageSize)
                        .timeout(new TimeValue(60, TimeUnit.SECONDS))
        );


        // 增加高亮，并且返回格式化数据，score排序
        SearchHit[] hits = client.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits();

        HashMap<String, List<Object>> dataMap = new HashMap<>();

        for (String index : indices) {
            dataMap.put(index, new ArrayList<>());
        }
        if (isAdmin) {
            for (SearchHit hit : hits) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                sourceAsMap.put("id", hit.getId());
                dataMap.get(hit.getIndex()).add(sourceAsMap);
            }
        } else {
            for (SearchHit hit : hits) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                if (!sourceAsMap.get("published").equals(true)) {
                    continue;
                }
                sourceAsMap.put("id", hit.getId());
                dataMap.get(hit.getIndex()).add(sourceAsMap);
            }
        }


        dataMap.keySet().removeIf(key -> dataMap.get(key).size() == 0);
        return dataMap;
    }

    @SneakyThrows
    private void initIndex() {
        Set<Class<?>> classSet = ClassUtils.getClassSet(config.getPkg()); // 获得该包下的所有类

        for (Class<?> aClass : classSet) {
            Index index = aClass.getDeclaredAnnotation(Index.class);
            if (index != null) { // 设置indexName
                String indexName = "";
                if (!index.value().equals("")) {
                    indexName = index.value();
                } else {
                    indexName = Utils.humpToLine(aClass.getSimpleName());
                }
                indexMap.put(aClass.getName(), indexName);


                HashMap<String, List<String>> typeListMap = new HashMap<>();
                for (String s : esTypes) {
                    typeListMap.put(s, new ArrayList<>());
                }

                HashMap<String, Field> map = new HashMap<>();

                Field[] declaredFields = aClass.getDeclaredFields();
                for (Field field : declaredFields) {
                    if (field.isAnnotationPresent(IndexId.class)) {
                        field.setAccessible(true);
                        indexIdMap.put(indexName, field);
                        continue;
                    }

                    IndexField indexField = field.getAnnotation(IndexField.class);
                    if (indexField != null) {
                        String fieldName = "";
                        field.setAccessible(true);
                        if (!indexField.value().equals("")) {
                            fieldName = indexField.value();
                        } else {
                            fieldName = field.getName();
                        }
                        map.put(fieldName, field);

                        if (indexField.isSearch()) {
                            typeListMap.get(parseType(field)).add(fieldName);
                        }
                    }
                }

                typeFieldMix.put(indexName, typeListMap);
                indexFiledMap.put(indexName, map);

                // 判断是否存在
                if (!isExistIndex(aClass)) {
                    createIndex(aClass);
                    log.info("auto createIndex {} in ElasticSearchService", indexName);
                } else {
                    updateIndex(aClass);
                    log.info("auto updateIndex {} in ElasticSearchService", indexName);
                }
            }
        }

        String[] split = dateFormat.split("\\|\\|");
        simpleDateFormat = new SimpleDateFormat(split[0]);
        allIndex         = indexFiledMap.keySet().toArray(ConstantPool.EMPTY_STRINGS);

        log.debug("initIndexMap=>{}", indexMap);
        log.debug("initIndexIdMap=>{}", indexIdMap);
        log.debug("initIndexFiledMap=>{}", indexFiledMap);
        log.debug("initTypeFieldMix=>{}", typeFieldMix);
    }

    @Data
    private class Meta {
        private       String              indexName;
        private       String              indexId;
        private final Map<String, Object> source = new HashMap<>();

        @SneakyThrows
        public Meta(Object o) {
            String className = o.getClass().getName();
            indexName = indexMap.get(className);
            Map<String, Field> fieldMap     = indexFiledMap.get(indexName);
            Field              indexIdField = indexIdMap.get(indexName);
            if (indexIdField != null) {
                indexId = indexIdField.get(o) == null ? "" : indexIdField.get(o) + "";
            } else {
                indexId = "";
            }

            for (Map.Entry<String, Field> entry : fieldMap.entrySet()) {
                Field  field = entry.getValue();
                Object value = field.get(o);
                if (value == null) continue;

                if (value.getClass().getTypeName().equals("java.util.Date")) {
                    value = simpleDateFormat.format(value);
                }

                IndexField indexField = field.getAnnotation(IndexField.class);
                if (indexField.value().equals("")) {
                    source.put(field.getName(), value);
                } else { // 如果有别名，则设置为别名
                    source.put(indexField.value(), value);
                }
            }
        }
    }

}
