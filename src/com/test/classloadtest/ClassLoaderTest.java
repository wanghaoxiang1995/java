package com.test.classloadtest;

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
        //testClassLoader();
        new ClassLoaderTest().testClassLoaderByConstant();
    }

    private void testClassLoaderByConstant() {
        System.out.println(0);
        Class<LoadedClass> loadedClassClass = LoadedClass.class;
        System.out.println(1);
        System.out.println(loadedClassClass);
        System.out.println(3);
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
