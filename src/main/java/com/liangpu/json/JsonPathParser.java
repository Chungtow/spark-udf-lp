package com.liangpu.json;

import java.util.ArrayList;
import java.util.List;

/**
 * JSONPath 解析器：将 path 文本解析为分段（segment）列表。
 *
 * <p>design ADR-1 支持的子集（对齐 MaxCompute 文档写法 + 超集扩展）：</p>
 * <ul>
 *   <li>{@code $} 根</li>
 *   <li>{@code $.key} / {@code $.k1.k2} 对象键（标识符）</li>
 *   <li>{@code $[n]} 数组下标（非负整数）</li>
 *   <li>{@code $.k[n]} / {@code $[n].k} 混合</li>
 *   <li>{@code $['key']} / {@code $['a.b']} 引号键（含点/空格/中文等特殊字符，支持反斜杠转义）</li>
 * </ul>
 *
 * <p>不支持的语法（解析即抛 {@link JsonPathException}，对齐 MC 对非法 path 报错）：</p>
 * <ul>
 *   <li>{@code $a}（$ 后非 . 或 [）</li>
 *   <li>{@code ..} 递归下降、{@code *} 通配符、{@code [?()]} 过滤、{@code @} 当前节点</li>
 *   <li>空段（尾点、空下标、空键）、未闭合括号</li>
 * </ul>
 */
public final class JsonPathParser {

    private JsonPathParser() {
    }

    /**
     * path 分段：对象键或数组下标。
     */
    public static final class Segment {
        private final String key;
        private final int index;
        private final boolean isIndex;

        private Segment(String key, int index, boolean isIndex) {
            this.key = key;
            this.index = index;
            this.isIndex = isIndex;
        }

        public static Segment key(String key) {
            return new Segment(key, 0, false);
        }

        public static Segment index(int index) {
            return new Segment(null, index, true);
        }

        public boolean isIndex() {
            return isIndex;
        }

        public String key() {
            return key;
        }

        public int index() {
            return index;
        }

        @Override
        public String toString() {
            return isIndex ? "[" + index + "]" : "." + key;
        }
    }

    /**
     * 解析 path 为分段列表；根 {@code $} 返回空列表；语法非法抛 {@link JsonPathException}。
     */
    public static List<Segment> parse(String path) {
        if (path == null || path.isEmpty()) {
            throw new JsonPathException("json_path 不能为空");
        }
        if (path.charAt(0) != '$') {
            throw new JsonPathException("非法 json_path（须以 $ 开头）: " + path);
        }
        List<Segment> segments = new ArrayList<Segment>();
        int i = 1;
        int n = path.length();
        while (i < n) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
                if (i >= n) {
                    throw new JsonPathException("json_path 以 '.' 结尾: " + path);
                }
                if (path.charAt(i) == '.') {
                    throw new JsonPathException("json_path 不支持 '..' 递归下降: " + path);
                }
                if (path.charAt(i) == '[') {
                    Seg seg = parseBracket(path, i);
                    segments.add(seg.segment);
                    i = seg.next;
                } else {
                    Seg seg = parseIdentifier(path, i);
                    segments.add(seg.segment);
                    i = seg.next;
                }
            } else if (c == '[') {
                Seg seg = parseBracket(path, i);
                segments.add(seg.segment);
                i = seg.next;
            } else {
                throw new JsonPathException("非法 json_path（'$' 后须为 '.' 或 '['）: " + path);
            }
        }
        return segments;
    }

    /**
     * 标识符键：从 start 读到 '.' / '[' / 结束；不允许本期不支持的字符。
     */
    private static Seg parseIdentifier(String path, int start) {
        int n = path.length();
        int j = start;
        while (j < n && path.charAt(j) != '.' && path.charAt(j) != '[') {
            j++;
        }
        String key = path.substring(start, j);
        if (key.isEmpty()) {
            throw new JsonPathException("json_path 存在空段: " + path);
        }
        for (int k = 0; k < key.length(); k++) {
            char ch = key.charAt(k);
            if (ch == '*' || ch == '?' || ch == '(' || ch == ')' || ch == '@'
                    || ch == ' ' || ch == ']' || ch == '\'' || ch == '"') {
                throw new JsonPathException("json_path 含不支持的字符（通配符/过滤/递归等）: " + path);
            }
        }
        return new Seg(Segment.key(key), j);
    }

    /**
     * 括号访问器：{@code $[0]} 非负整数下标 / {@code $['key']} 引号键（支持反斜杠转义）。
     */
    private static Seg parseBracket(String path, int start) {
        int n = path.length();
        int j = start + 1; // 跳过 '['
        if (j >= n) {
            throw new JsonPathException("json_path '[' 未闭合: " + path);
        }
        char c = path.charAt(j);
        if (c == '\'') {
            j++;
            StringBuilder sb = new StringBuilder();
            boolean closed = false;
            while (j < n) {
                char ch = path.charAt(j);
                if (ch == '\\') {
                    if (j + 1 >= n) {
                        throw new JsonPathException("json_path 转义不完整: " + path);
                    }
                    sb.append(path.charAt(j + 1));
                    j += 2;
                } else if (ch == '\'') {
                    j++;
                    closed = true;
                    break;
                } else {
                    sb.append(ch);
                    j++;
                }
            }
            if (!closed) {
                throw new JsonPathException("json_path 引号未闭合: " + path);
            }
            if (j >= n || path.charAt(j) != ']') {
                throw new JsonPathException("json_path '[' 未正确闭合: " + path);
            }
            String key = sb.toString();
            if (key.isEmpty()) {
                throw new JsonPathException("json_path 空键: " + path);
            }
            return new Seg(Segment.key(key), j + 1);
        }
        // 非负整数下标
        int k = j;
        boolean anyDigit = false;
        while (k < n && Character.isDigit(path.charAt(k))) {
            k++;
            anyDigit = true;
        }
        if (!anyDigit) {
            throw new JsonPathException("json_path 下标须为非负整数或引号键: " + path);
        }
        if (k >= n || path.charAt(k) != ']') {
            throw new JsonPathException("json_path '[' 未正确闭合: " + path);
        }
        final String digits = path.substring(j, k);
        int index;
        try {
            index = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new JsonPathException("json_path 下标溢出: " + path, e);
        }
        return new Seg(Segment.index(index), k + 1);
    }

    private static final class Seg {
        private final Segment segment;
        private final int next;

        private Seg(Segment segment, int next) {
            this.segment = segment;
            this.next = next;
        }
    }
}
