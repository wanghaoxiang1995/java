package com.test.authortest.anotherpackage;

/**
 * Created by Administrator on 2017/7/3 0003.
 */
class DefaultAuthorityTest {
    private String msg;
    public DefaultAuthorityTest() {
        System.out.println("构造DefaultAuthorityTest");
    }
    public static DefaultAuthorityTest getInstance() {
        return new DefaultAuthorityTest();
    }

    public static String pubMsg = "public message";

    public void setMsg(String msg) {
        this.msg = msg;
        System.out.println(this.msg);
    }

    public String getMsg() {
        System.out.println(msg);
        return msg;
    }
}
