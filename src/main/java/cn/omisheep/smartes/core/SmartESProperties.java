package cn.omisheep.smartes.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zhouxinchen[1269670415@qq.com]
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "smartes")
public class SmartESProperties {
    private ClientConfig  client;
    private ServiceConfig service;

    @Data
    public static class ClientConfig {
        private String  hostname;
        private Integer port;
        private String  scheme;
        private String  username;
        private String  password;
    }

    @Data
    public static class ServiceConfig {
        private String pkg; // 实体类扫描的包名
        private String preTags         = "<span class=\"highlight\">"; // 高亮显示的前缀
        private String postTags        = "</span>"; // 高亮显示的后缀
        private int    defaultPageSize = 10;
    }
}
