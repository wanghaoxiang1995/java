package com.test.newiotest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 类名：NewIOTest
 * 类路径：com.test.newiotest.NewIOTest
 * 创建者：王浩翔
 * 创建时间：2017-11-15 15:51
 * 项目：java
 * 描述：
 */
public class NewIOTest {
    public static void main(String[] args) throws IOException {
        File file = new File("D:\\temp\\50043514541.mp4");
        long length = file.length();
        ByteBuffer byteBuffer = ByteBuffer.allocate(8096);
        FileChannel fc = new FileInputStream(file).getChannel();
        long index = 0;
        while ((index+=8096)<length){
            fc.read(byteBuffer);
            byteBuffer.flip();
            System.out.println(byteBuffer.asIntBuffer().get());
            byteBuffer.clear();
        }


        System.out.println(length);
    }
}
