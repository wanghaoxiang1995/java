package com.test.classloadtest;

/**
 * 类名：LoadedClass
 * 类路径：com.test.classloadtest.LoadedClass
 * 创建者：王浩翔
 * 创建时间：2017-08-17 17:46
 * 项目：java
 * 描述：
 */
public class LoadedClass {
    static {
        System.out.println("loading LoadedClass");
    }
    static int count = 0;
    public LoadedClass() {
        System.out.println(++count);
    }
}
