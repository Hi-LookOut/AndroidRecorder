package com.hm.leetcode;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author hm
 * @version [v1, 2020-05-02]
 * @Describe: 无重复字符的最长子串
 */


public class LT1 {

    public static void main(String[] args) {
        String s = "abba";
        //abba

        System.out.println("" + lengthOfLongestSubstring(s));
        System.out.println("" + lengthOfLongestSubstring2(s));
    }

//    执行用时 :7 ms, 在所有 Java 提交中击败了82.77%的用户内存消耗 :40.4 MB, 在所有 Java 提交中击败了5.20%的用户

    public static int lengthOfLongestSubstring2(String s) {
        int n = s.length(), ans = 0;
        int start = 0;
        Map<Character, Integer> map = new HashMap<>();

        for (int end = 0; end < n; end++) {
            if (map.containsKey(s.charAt(end))) {
                ans = Math.max(ans, end - start);
                if (start<map.get(s.charAt(end))+1){
                    start = map.get(s.charAt(end)) + 1;
                }

            }
            map.put(s.charAt(end), end);

        }


        if (start == 0) {
            return n;
        } else {
            ans = Math.max(ans, n - start);
        }


        return ans;
    }

    /**
     *  执行用时 :6 ms, 在所有 Java 提交中击败了85.22%的用户内存消耗 :39.6 MB, 在所有 Java 提交中击败了5.89%的用户
     */
    public static int lengthOfLongestSubstring(String s) {
        int length = s.length();
        StringBuilder temp = new StringBuilder();
        int MaxLength = 0;
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < temp.length(); j++) {
                if (s.charAt(i) == temp.charAt(j)) {
                    if (MaxLength < temp.length()) {
                        MaxLength = temp.length();
                    }
                    temp.delete(0, j + 1);
                    break;
                }
            }


            temp.append(s.charAt(i));
        }
        if (MaxLength < temp.length()) {
            MaxLength = temp.length();
        }
        return MaxLength;
    }



}

