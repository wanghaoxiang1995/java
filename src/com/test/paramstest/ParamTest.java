package com.test.paramstest;

/**
 * Created by Administrator on 2017/7/3 0003.
 */
public class ParamTest {
    public static void paramsTest(Object object) {
        object = "new Object";
        System.out.println("paramsTest:"+object);
    }

    public static void main(String[] args) {
        Object object = "old Object";
        System.out.println(object);

        paramsTest(object);
        System.out.println(object);
    }
}
