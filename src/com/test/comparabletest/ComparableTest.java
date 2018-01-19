package com.test.comparabletest;

import java.util.ArrayList;
import java.util.Collections;

/**
 * 描述：
 * 项目：java
 * 类路径： com.test.comparabletest.ComparableTest
 * 创建人：wanghaoxiang
 * 时间： 18-1-19 下午6:31
 **/
public class ComparableTest implements Comparable<ComparableTest> {

    private static int count = 0;

    private Integer id = ++count;


    public static void main(String[] args) {
        ComparableTest com1 = new ComparableTest();
        ComparableTest com2 = new ComparableTest();
        ComparableTest com3 = new ComparableTest();
        ComparableTest com4 = new ComparableTest();
        ArrayList<ComparableTest> list = new ArrayList<>();
        list.add(com3);
        list.add(com2);
        list.add(com4);
        list.add(com1);
        com2.id = null;
        Collections.sort(list);
        for (ComparableTest comparableTest : list) {
            System.out.println(comparableTest);
        }
        System.out.println(com2.compareTo(com3));
        System.out.println(com1.compareTo(com2));
        System.out.println(com1.compareTo(com4));
    }


    @Override
    public int compareTo(ComparableTest o) {
        if (this.id == null) {
            return 1;
        }
        if (o.id == null) {
            return -1;
        }
        return this.id - o.id;
    }

    @Override
    public String toString() {
        return String.format("id:%d",id);
    }
}
