package com.test.dynamicproxytest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 类名：DynamicProxyHandlerTest
 * 类路径：com.test.dynamicproxytest.DynamicProxyHandlerTest
 * 创建者：王浩翔
 * 创建时间：2017-08-23 13:50
 * 项目：java
 * 描述：
 */
public class DynamicProxyHandlerTest implements InvocationHandler {

    //代理对象
    private Object proxied;
    DynamicProxyHandlerTest(Object proxied) {
        this.proxied = proxied;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //原对象和代理对象实现了相同接口，代理对象可以被原对象方法识别
        //InvocationHandler进行代理对象要进行的代理操作（在invoke里添加附加操作）
        //Proxy类生成代理对象
        //Proxy.newProxyInstance(ClassLoader classLoader,Class[] interfaces,InvocationHandler)参数为类加载器，生成的代理实现的接口，代理方法处理器，可以生成代理对象
        //原对象激活方法
        //return method.invoke(proxy,args);
        //代理对象激活方法
        System.out.println("proxy:");
        return method.invoke(proxied,args);
    }
}
