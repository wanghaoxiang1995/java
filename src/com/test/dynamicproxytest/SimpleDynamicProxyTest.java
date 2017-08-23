package com.test.dynamicproxytest;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * 类名：SimpleDynamicProxyTest
 * 类路径：com.test.dynamicproxytest.SimpleDynamicProxyTest
 * 创建者：王浩翔
 * 创建时间：2017-08-23 14:01
 * 项目：java
 * 描述：
 */
public class SimpleDynamicProxyTest {

    public static void consumerImpl(OriginalImpl original){
        original.print();
        original.typeInfo();
    }

    public static void consumer(Original original) {
        original.print();
        System.out.println(original.typeInfo());
    }

    public static void main(String[] args) {
        OriginalImpl original = new OriginalImpl();
        consumer(original);
        Original proxy = (Original) Proxy.newProxyInstance(
                Original.class.getClassLoader(),
                new Class[]{Original.class,Serializable.class},
                new DynamicProxyHandlerTest(original)
        );
        consumer(proxy);
        //!无法生成具体类的代理，只能生成接口的代理
        //OriginalImpl impl =(OriginalImpl) Proxy.newProxyInstance(
        //        Original.class.getClassLoader(),
        //        new Class[]{Original.class,Serializable.class,OriginalImpl.class},
        //        new DynamicProxyHandlerTest(original)
        //);

    }
}
