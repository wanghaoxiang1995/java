package com.test.defaluttest;

/**
 * Created by Administrator on 2017/7/6 0006.
 */
public class DefaultTest {
    private int var;


    public void setVar() {
        System.out.println(getClass());
    }

    public static void main(String[] args) {
        new DefaultTest().setVar();
    }
}
