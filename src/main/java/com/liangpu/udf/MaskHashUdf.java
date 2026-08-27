package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * mask_hash(str)：脱敏哈希（对齐 MaxCompute MASK_HASH）。
 *
 * <p>契约（ADR-13，MC 未公开算法）：SHA-256 摘要的十六进制表示（32 字节 → 64 字符，小写），
 * 同输入同输出、不同输入大概率不同、不可逆。不要求与 MC 输出结果一致。</p>
 *
 * <p>边界：str 为 SQL NULL → NULL；非字符串类型入参 → NULL；空串输入返回非 NULL 的
 * 固定哈希（SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855）。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT mask_hash('abc');  -- ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
 * </pre>
 */
@ExpressionDescription(
        usage = "mask_hash(str) - 脱敏哈希：SHA-256 十六进制（固定 64 字符小写），不可逆；NULL 或非字符串类型入参返回 NULL。",
        arguments = "str - 待脱敏字符串")
public class MaskHashUdf extends GenericUDF {

    private transient PrimitiveObjectInspector strOI; // 第 1 参 OI（用于类型判断与取值）

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("mask_hash 需要 1 个参数: mask_hash(str)，实际 " + args.length);
        }
        // 非字符串类型入参不抛错，运行时返回 NULL（契约：非字符串 → NULL）；
        // NULL 字面量（VOID）放行，运行时返回 NULL
        if (args[0].getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(0, "mask_hash 参数必须为字符串");
        }
        strOI = (PrimitiveObjectInspector) args[0];
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object v = args[0].get();
        if (v == null) {
            return null;
        }
        PrimitiveObjectInspector.PrimitiveCategory cat = strOI.getPrimitiveCategory();
        if (cat == PrimitiveObjectInspector.PrimitiveCategory.VOID) {
            return null; // NULL 字面量
        }
        if (cat != PrimitiveObjectInspector.PrimitiveCategory.STRING) {
            return null; // 非字符串类型入参 → NULL
        }
        try {
            // 引擎中 string 值为 Text（Writable），统一经 OI 取值
            String s = PrimitiveObjectInspectorUtils.getString(v, strOI);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new HiveException("SHA-256 算法不可用", e); // JDK 必含，理论不可达
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    @Override
    public String getDisplayString(String[] children) {
        return "mask_hash(" + (children.length > 0 ? children[0] : "") + ")";
    }
}
