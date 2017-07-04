package com.extendstest;

/**
 * Created by Administrator on 2017/7/4 0004.
 */
public class SuperClass {
    public SuperClass() {
        System.out.println(this.getClass());
        printClass();
        printMSG();
    }
    private void printClass() {
        System.out.println(getClass());
    }

    protected void printMSG() {
        System.out.println("super class print");
    }
}
