package com.test.referencetest;

import java.lang.ref.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.WeakHashMap;

/**
 * 类名：ReferenceTest
 * 类路径：com.test.referencetest.ReferenceTest
 * 创建者：王浩翔
 * 创建时间：2017-10-27 10:54
 * 项目：java
 * 描述：
 */
public class ReferenceTest {

    private static ReferenceQueue<BigObject> rq = new ReferenceQueue<BigObject>();
    private static WeakHashMap<String,Object> weakMap = new WeakHashMap<>();

    public static void checkRQ() {
        Reference<? extends BigObject> poll = rq.poll();
        if (poll != null) {
            System.out.println("In queue ref" + poll);
            System.out.println("In queue: %s" + poll.get());
        }
    }

    public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        LinkedList<Reference<BigObject>> softRefList = getReferenceLinkedList("Soft Reference %d", SoftReference.class, BigObject.class);
        LinkedList<Reference<BigObject>> weakRefList = getReferenceLinkedList("Weak Reference %d", WeakReference.class, BigObject.class);
        SoftReference<BigObject> singleSoftRef = new SoftReference<>(new BigObject("unRefQueue soft reference"));
        WeakReference<BigObject> singleWeakRef = new WeakReference<>(new BigObject("unRefQueue weak reference"));
        System.out.println("prepare gc");
        System.gc();
        LinkedList<Reference<BigObject>> phantomRefList = getReferenceLinkedList("Phantom Reference %d", PhantomReference.class, BigObject.class);
        System.out.println("end");

    }

    public static <R extends Reference<C>,C> LinkedList<Reference<C>> getReferenceLinkedList(String identifyExpression,Class<R> rClass,Class<C> cClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        int size = 10;
        LinkedList<Reference<C>> refList = new LinkedList<>();
        Constructor<C> constructor = cClass.getConstructor(String.class);
        Constructor<R> referenceConstructor = rClass.getConstructor(Object.class, ReferenceQueue.class);
        for (int i = 0; i < size; i++) {
            String name = String.format(identifyExpression, i);
            C c = constructor.newInstance(name);
            refList.add(referenceConstructor.newInstance(c,rq));
            System.out.println("Created "+refList.getLast());
            weakMap.put(name,c);
            checkRQ();
        }
        return refList;
    }

}

class BigObject {
    private static final int size = 10086;
    private int[] memberOccupy = new int[size];
    private String name;

    public BigObject(String identify) {
        name = identify;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println(String.format("Finalizing %s", name));
    }
}