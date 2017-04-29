// #jgrab com.google.guava:guava:19.0
package com.zetcode.initializecollectionex;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;

/**
 * Source code copied from http://zetcode.com/articles/guava/
 */
public class UsesGuava {

    public static void main(String[] args) {

        Map<String, Integer> items = ImmutableMap.of("coin", 3, "glass", 4, "pencil", 1);

        items.entrySet()
                .stream()
                .forEach(System.out::println);

        List<String> fruits = Lists.newArrayList("orange", "banana", "kiwi",
                "mandarin", "date", "quince");

        for (String fruit: fruits) {
            System.out.println(fruit);
        }
    }
}
