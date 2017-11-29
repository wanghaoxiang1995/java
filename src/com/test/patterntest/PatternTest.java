package com.test.patterntest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 类名：PatternTest
 * 类路径：com.test.patterntest.PatternTest
 * 创建者：王浩翔
 * 创建时间：2017-11-29 15:21
 * 项目：java
 * 描述：
 */
public class PatternTest {
    public static void main(String[] args) {
        String matches = "20509:\\d+";
        String str = "8560225:80854396;152844862:30044;20000:29534;20509:28315;20509:28316;20509:28317;20509:6145171;20509:115781;20021:20213;149422948:419664911;122216348:29444;122276111:20525;13021751:544459738;122216345:29458;122216608:42007;1627207:28334;1627207:28341";

        Pattern sizePattern = Pattern.compile(matches);
        Matcher matcher = sizePattern.matcher(str);
        while (matcher.find()){
            System.out.println(matcher.group());
        }

        System.out.println("end");
    }
}
