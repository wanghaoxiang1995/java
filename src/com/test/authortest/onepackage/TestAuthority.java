package com.test.authortest.onepackage;

import com.test.authortest.anotherpackage.InstanceGetter;

/**
 * Created by Administrator on 2017/7/3 0003.
 */
public class TestAuthority {
    public static void main(String[] args) {
        InstanceGetter.getDefaultAuthorityTestInstance();
        new InstanceGetter();
    }
}
