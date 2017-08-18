package com.test.foreachtest;

/**
 * Created by Administrator on 2017/6/30 0030.
 */
public class TestForeach {
    public static void main(String[] args) {
        int[] ints = {1,2,3};
        for (int i:ints) {
            i++;
        }
        for (int i:ints) {
            System.out.println(i);
            i = 5;
        }
        for (int i: ints) {
            System.out.println(i);
        }
    }
}
