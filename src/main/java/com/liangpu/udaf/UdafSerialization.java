package com.liangpu.udaf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;

/**
 * UDAF 中间态（partial）序列化工具。
 *
 * <p>Spark 2.4 集群对 Hive UDAF 的 partial 传输机制（hiveUDFs.scala
 * {@code AggregationBufferSerDe}）：map 端 {@code terminatePartial} 产物会被
 * 按 {@code init(Mode.PARTIAL2, ...)} 返回的 OI 序列化，reduce 端反序列化后
 * 直接传给 {@code merge}。因此 partial 对象必须与 partial 模式 OI 匹配。
 *
 * <p>为对齐已在集群验证通过的 {@code udaf_string_agg}（StringAggUDAF）模式：
 * <ul>
 *   <li>partial 统一为 {@link Text}（Hive writable String），OI 用
 *       {@code writableStringObjectInspector}——Spark 2.4 序列化/反序列化后
 *       还原为 Text，merge 端 {@code partial.toString()} 无损取回字符串；</li>
 *   <li>字符串载体为 Base64（Java 序列化字节），保证任意二进制中间态可传输。</li>
 * </ul>
 */
public final class UdafSerialization {

    private UdafSerialization() {
    }

    /** Java 序列化为字节数组。 */
    static byte[] toBytes(Object o) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(o);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("UDAF partial 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 字节数组反序列化为对象。 */
    static Object fromBytes(byte[] bytes) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("UDAF partial 反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 中间态 → Base64 编码的 {@link Text}（Spark 2.4 partial 传输载体，
     * 对齐 {@code udaf_string_agg} 已验证模式）。
     */
    public static Text partialOfText(Object o) {
        return new Text(Base64.getEncoder().encodeToString(toBytes(o)));
    }

    /**
     * Spark 2.4 反序列化后还原的 partial（{@link Text} 或 java String）→ 中间态对象。
     */
    public static Object fromTextPartial(Object partial) {
        if (partial == null) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(partial.toString());
        return fromBytes(bytes);
    }

    /** 兼容旧路径：中间态 → Java 序列化 ByteBuffer（Hive 原生执行）。 */
    public static ByteBuffer partialOf(Object o) {
        return ByteBuffer.wrap(toBytes(o));
    }

    /**
     * 兼容旧路径：partial（ByteBuffer/byte[]/BytesWritable/Text/String）→ 中间态对象。
     */
    public static Object fromPartial(Object partial) {
        if (partial == null) {
            return null;
        }
        if (partial instanceof ByteBuffer) {
            ByteBuffer bb = (ByteBuffer) partial;
            byte[] dst = new byte[bb.remaining()];
            bb.get(dst);
            return fromBytes(dst);
        }
        if (partial instanceof byte[]) {
            return fromBytes((byte[]) partial);
        }
        if (partial instanceof BytesWritable) {
            BytesWritable bw = (BytesWritable) partial;
            return fromBytes(java.util.Arrays.copyOfRange(bw.getBytes(), 0, bw.getLength()));
        }
        if (partial instanceof Text || partial instanceof String) {
            return fromTextPartial(partial);
        }
        throw new IllegalArgumentException("不支持的 UDAF partial 类型: "
                + partial.getClass().getName());
    }
}
