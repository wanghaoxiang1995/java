package com.anotherpackage;

/**
 * Created by Administrator on 2017/7/3 0003.
 */
public class InstanceGetter {
    public static DefaultAuthorityTest getDefaultAuthorityTestInstance() {
        return DefaultAuthorityTest.getInstance();
    }
    public InstanceGetter() {
        System.out.println("package InstanceGetter construct");
    }
}
