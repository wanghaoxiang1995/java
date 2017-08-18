package com.test.collecttest;

import org.junit.Test;

import java.util.*;

/**
 * 类名：CollectionTest
 * 类路径：com.test.collecttest.CollectionTest
 * 创建者：王浩翔
 * 创建时间：2017-08-08 11:53
 * 项目：java
 * 描述：
 */
public class CollectionTest {

    @Test
    public void collectionTest(){
        List<Object> objects = new ArrayList<>(20);
        objects.add(1);
        objects.add(5);
        objects.add(3);
        objects.add(9);
        objects.add(6);
        List<Object> sub = objects.subList(2, 4);
        sub.add(2);
        sub.remove(0);
        System.out.println(objects);
        System.out.println(sub);
        System.out.println(objects.containsAll(sub));
        List<Object> linkList = new LinkedList<>(sub);
        linkList.remove(0);
        linkList.add(6);

        linkList.add(8);
        System.out.println(objects.containsAll(linkList));
        System.out.println(objects.retainAll(linkList));
        System.out.println(objects);
        System.out.println(linkList);
        Integer[] integers = linkList.toArray(new Integer[0]);
        for (Integer integer : integers) {
            System.out.println(integer);
        }

    }
}
