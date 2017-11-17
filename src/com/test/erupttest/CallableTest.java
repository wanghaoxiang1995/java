package com.test.erupttest;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * 类名：CallableTest
 * 类路径：com.test.erupttest.CallableTest
 * 创建者：王浩翔
 * 创建时间：2017-11-13 12:21
 * 项目：java
 * 描述：
 */
public class CallableTest {
    class SimpleCaller implements Callable<String>{
        int identity;
        public SimpleCaller(int identity) {
            this.identity = identity;
        }

        @Override
        public String call() throws Exception {
            return String .format("this caller id is %d",identity);
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Integer inte = 1;
        if (Objects.equals(1,inte)) {
            System.out.println("fas");
        }
        CallableTest callableTest = new CallableTest();
        ExecutorService exec = Executors.newCachedThreadPool();
        int count = 0;
        Future<String> submit = exec.submit(callableTest.new SimpleCaller(++count));
        String s = submit.get();
        System.out.println(s);
    }
}
