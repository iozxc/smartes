package cn.omisheep.smartes.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zhouxinchen[1269670415@qq.com]
 * @since 1.0.0
 */
public class Utils {
    private static final Pattern humpPattern = Pattern.compile("[A-Z]");

    public static String humpToLine(String str) {
        Matcher      matcher = humpPattern.matcher(str);
        StringBuffer sb      = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "_" + matcher.group(0).toLowerCase());
        }
        matcher.appendTail(sb);
        if (sb.charAt(0) == '_') {
            return sb.substring(1);
        }
        return sb.toString();
    }

}
