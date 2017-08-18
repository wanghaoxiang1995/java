package com.test.classtest;

/**
 * Created by Administrator on 2017/7/3 0003.
 */
public class TestBO {
    static String staticMSG;
    static {
        staticMSG = "static msg";
        System.out.println("static:"+staticMSG);
    }
    String msg;
    {
        msg = "message";
        System.out.println("instance msg:" + msg);
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println("finalize TestBO");
        super.finalize();
    }
}
