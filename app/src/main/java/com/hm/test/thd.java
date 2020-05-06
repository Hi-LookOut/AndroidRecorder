package com.hm.test;

import java.util.ArrayList;

/**
 * @author hm
 * @version [v3.6.2, 2020-04-23]
 * @Describe:
 */


public class thd  implements  Runnable{
    private ArrayList<String> data = new ArrayList<>();
    private final Object lock = new Object();


    @Override
    public void run() {
//        System.out.println("1111111");
        while (true) {
            if (data.isEmpty()) {
//                System.out.println("3333333");
                synchronized (lock) {
                    try {
                        System.out.println("44444444"+Thread.currentThread().getName());
                        lock.wait();
                        System.out.println("55555555"+Thread.currentThread().getName());
//                        Thread.sleep(1000);
                        if (!data.isEmpty()){

                            System.out.println(data.get(0));
                            data.remove(0);
                            System.out.println("fuck"+Thread.currentThread().getName());
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
//                    System.out.println("66666666");
                }
            }
        }
    }


    public void push(String name) {
        data.add(name);
        synchronized (lock) {
            lock.notifyAll();
        }
    }

}
