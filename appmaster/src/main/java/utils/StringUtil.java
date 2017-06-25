package utils;

/**
 * Created by zhuifeng on 2017/6/24.
 */
public class StringUtil {
    public static boolean isEmpty(String str){
        return null == str || str.isEmpty();
    }

    public static boolean isNotEmpty(String str){
        return !isEmpty(str);
    }
}
