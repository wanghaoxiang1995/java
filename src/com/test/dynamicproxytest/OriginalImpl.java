package com.test.dynamicproxytest;

/**
 * 类名：OriginalImpl
 * 类路径：com.test.dynamicproxytest.OriginalImpl
 * 创建者：王浩翔
 * 创建时间：2017-08-23 14:06
 * 项目：java
 * 描述：
 */
public class OriginalImpl implements Original {

    @Override
    public void print() {
        System.out.println("this is interface impl");
    }

    @Override
    public Class typeInfo() {
        return getClass();
    }
}
