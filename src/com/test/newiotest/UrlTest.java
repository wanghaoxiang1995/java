package com.test.newiotest;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * 类名：UrlTest
 * 类路径：com.test.newiotest.UrlTest
 * 创建者：王浩翔
 * 创建时间：2017-11-15 16:04
 * 项目：java
 * 描述：
 */
public class UrlTest {

    public static void main(String[] args) throws IOException {
        String tmpdir = System.getProperty("tmpdir");
        int len = 8096;
        URL url = new URL("https://cloud.video.taobao.com/play/u/3191392680/p/1/e/6/t/1/50037078547.mp4");
        URLConnection urlConnection = url.openConnection();
        urlConnection.connect();
        BufferedInputStream bufIn = new BufferedInputStream(urlConnection.getInputStream());
        File file = new File("D:\\temp\\urltest2.mp4");
        file.createNewFile();
        BufferedOutputStream bufOut = new BufferedOutputStream(new FileOutputStream(file));
        byte[] bytes = new byte[8096];
        int readLen = -1;
        while ((readLen = bufIn.read(bytes))>-1){
            bufOut.write(bytes,0,readLen);
            bufOut.flush();
        }
        bufOut.flush();
        bufIn.close();
        bufOut.close();

    }
}
