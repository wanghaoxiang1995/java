package com.test.calendartest;

import java.util.Calendar;
import java.util.Date;

/**
 * 类名：CalendarTest
 * 类路径：com.test.calendartest.CalendarTest
 * 创建者：王浩翔
 * 创建时间：2017-11-17 16:50
 * 项目：java
 * 描述：
 */
public class CalendarTest {
    public static void main(String[] args) {
        Calendar instance = Calendar.getInstance();
        Date time = instance.getTime();
        System.out.println(time);
    }
}
