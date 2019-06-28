package cn.admin.test;

public class MengbiTest {

    static int i = 0;

    public static void main(String[] args) {
        mengbi("懵逼");
    }

    static void mengbi(String str) {
        System.out.println(i ++);
        System.out.println(str);
        mengbi(str);
    }
}
