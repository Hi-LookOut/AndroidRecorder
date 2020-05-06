package com.hm.test;

/**
 * @author hm
 * @version [v3.6.2, 2020-04-23]
 * @Describe:
 */


public class ttest {

    public static void main(String[] args) {
        thd test=new thd();
        for(int i=0;i<4;i++){
            new Thread(test).start();
        }

//        new Thread(test).start();
//        new Thread(test).start();
//        new Thread(test).start();
//        System.out.println("00000000000");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("-1-1-1-1-1-1-");
        test.push("hm");
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        System.out.println("-2-2-2-2-2-2-");
        test.push("ncy");
    }
}
