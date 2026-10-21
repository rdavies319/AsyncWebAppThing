package com.rdavies.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/** Finds form inputs in rendered pages, so assertions don't depend on attribute order. */
final class TestHtml {
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>");

    private TestHtml() {}

    /** The whole {@code <input>} tag with this name and value, wherever its other attributes fall. */
    static String input(String html, String name, String value) {
        Matcher m = INPUT.matcher(html);
        while (m.find()) {
            String tag = m.group();
            if (tag.contains("name=\"" + name + "\"") && tag.contains("value=\"" + value + "\"")) {
                return tag;
            }
        }
        return fail("no input " + name + "=" + value + " in:\n" + html);
    }

    static boolean isChecked(String html, String name, String value) {
        return input(html, name, value).contains(" checked");
    }

    static boolean isDisabled(String html, String name, String value) {
        return input(html, name, value).contains(" disabled");
    }
}
