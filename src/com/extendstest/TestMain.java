package com.extendstest;

import org.junit.Test;

/**
 * Created by Administrator on 2017/7/4 0004.
 */
public class TestMain {

    @Test
    public void testPrivateMethodOfInheritance() {
        System.out.println("super class:");
        new SuperClass();

        System.out.println("derived class:");
        new DerivedClass();
    }
}
