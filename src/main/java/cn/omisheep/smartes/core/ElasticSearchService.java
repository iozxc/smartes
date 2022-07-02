package cn.omisheep.smartes.core;

import lombok.SneakyThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * @author zhouxinchen[1269670415@qq.com]
 * @since 1.0.0
 */
@SuppressWarnings("all")
public interface ElasticSearchService {
    boolean isIndex(Class<?> clz);

    @SneakyThrows
    boolean createIndex(Class<?> clz);

    boolean isExistIndex(Class<?> clz) throws IOException;

    boolean isAllExistIndex() throws IOException;

    boolean updateIndex(Class<?> clz) throws Exception;

    @SneakyThrows
    <E> int insert(E o);

    @SneakyThrows
    <E> boolean insert(List<E> list);

    @SneakyThrows
    <E> int update(E o);

    @SneakyThrows
    <E> boolean update(List<E> list);

    @SneakyThrows
    int delete(String clzName, String id);

    @SneakyThrows
    boolean delete(String clzName, List<String> list);

    HashMap<String, List<Object>> search(String keyword, Integer pageNo, Integer pageSize, String type,
                                         String index, String must, String should, Boolean isHighlight);

    HashMap<String, List<Object>> list(Integer pageNo, Integer pageSize, String index, String sortName, String order, boolean isAdmin);
}
