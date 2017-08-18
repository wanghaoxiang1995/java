package com.test.classloadtest;

import com.alibaba.fastjson.JSON;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 类名：ClassLoaderTest
 * 类路径：com.test.classloadtest.ClassLoaderTest
 * 创建者：王浩翔
 * 创建时间：2017-08-17 17:45
 * 项目：java
 * 描述：
 */
public class ClassLoaderTest {

    public static void main(String[] args) {
        new ClassLoaderTest().testClassLoaderByConstant();
        //testClassLoader();
    }

    private void testClassLoaderByConstant() {
        System.out.println(0);
        Class<LoadedClass> loadedClassClass = LoadedClass.class;
        System.out.println(1);
        System.out.println(loadedClassClass);
        System.out.println(3);
        Class<? extends Class> aClass = loadedClassClass.getClass();
        System.out.println(aClass);
        for (Method method : aClass.getMethods()) {
            //try {
            System.out.println("~~---------------------");
                System.out.println(method);
                System.out.println(JSON.toJSONString(method.getParameterAnnotations()));
                System.out.println(JSON.toJSONString(method.getParameterTypes()));
                System.out.println(method.getParameterCount());
                System.out.println(method.getParameters());
                System.out.println(method.getTypeParameters());
            System.out.println("__------------------");
            if (method.getParameterCount()==0) {
                try {
                    System.out.println(method.invoke(loadedClassClass));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
                //System.out.println(method.invoke(loadedClassClass).toString());
            //} catch (IllegalAccessException e) {
            //    e.printStackTrace();
            //} catch (InvocationTargetException e) {
            //    e.printStackTrace();
            //}
        }

    }

    private static void testClassLoader() {
        LoadedClass[] loadedClasses = new LoadedClass[10];
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int j = 0;
                    do {
                        if (loadedClasses[j] == null) {
                            loadedClasses[j] = new LoadedClass();
                            break;
                        }
                    } while (j++ < 10);
                }
            }).start();
        }
        System.out.println(loadedClasses[0] == null);
        while (loadedClasses[0] == null) {
            System.out.println("wait");
        }
        int j = -1;
        for (int i = 0; i < 10; i++) {
            if (loadedClasses[i] != null) {
                if (j >= 0) {
                    System.out.println(loadedClasses[i].getClass() == loadedClasses[j].getClass());
                }
                j = i;
                System.out.println(j);
            }
        }
    }
}
