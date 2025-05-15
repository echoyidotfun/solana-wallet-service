package com.wallet.service.utils;

public class StringFormatUtils {

    /**
     * 格式化字符串（如地址、签名），保留前缀和后缀，中间用 "..." 替代。
     *
     * @param input The string to format.
     * @param prefixLength The length of the prefix to keep.
     * @param suffixLength The length of the suffix to keep.
     * @return The formatted string, or the original string if it's too short or null.
     */
    public static String formatHashShort(String input, int prefixLength, int suffixLength) {
        if (input == null || input.length() <= prefixLength + suffixLength) {
            return input; // 如果字符串太短或为null，则返回原样
        }
        return String.format("%s...%s", input.substring(0, prefixLength), input.substring(input.length() - suffixLength));
    }

    /**
     * 格式化字符串（如地址、签名），保留前4位和后4位，中间用 "..." 替代。
     *
     * @param hash The string to format.
     * @return The formatted string (e.g., "2zMM...uauv").
     */
    public static String formatAddress(String hash) {
        return formatHashShort(hash, 4, 4);
    }

    // 可以根据需要添加其他格式化方法，例如专门为签名设计的不同长度的方法等
}
