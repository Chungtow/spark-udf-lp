package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.BytesWritable;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_geogfromwkb 单元测试：WKB 字节 → WKT；byte[]/BytesWritable 双入参；非法 WKB 报错。
 */
public class StGeogfromwkbUdfTest {

    /** POINT (2 4) little-endian WKB：01 01000000 0000000000000040 0000000000001040 */
    private static final String POINT_2_4_LE = "010100000000000000000000400000000000001040";
    /** POINT (2 4) big-endian WKB（api-spec 契约示例） */
    private static final String POINT_2_4_BE = "000000000140000000000000004010000000000000";

    private StGeogfromwkbUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StGeogfromwkbUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaByteArrayObjectInspector
        });
    }

    private GenericUDF.DeferredObject[] args(Object... values) {
        GenericUDF.DeferredObject[] result = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new GenericUDF.DeferredJavaObject(values[i]);
        }
        return result;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @Test
    public void testByteArrayLittleEndian() throws Exception {
        assertEquals("POINT (2 4)", udf.evaluate(args(hexToBytes(POINT_2_4_LE))));
    }

    @Test
    public void testByteArrayBigEndianContractExample() throws Exception {
        // api-spec 契约示例的 BE hex 也能解析
        assertEquals("POINT (2 4)", udf.evaluate(args(hexToBytes(POINT_2_4_BE))));
    }

    @Test
    public void testBytesWritableInput() throws Exception {
        assertEquals("POINT (2 4)", udf.evaluate(args(new BytesWritable(hexToBytes(POINT_2_4_LE)))));
    }

    @Test
    public void testNegativeCoordWkb() throws Exception {
        // 用 toWkb 构造 -90/-180 的点，验证负坐标往返
        byte[] wkb = GeoUtils.toWkb(GeoUtils.parseWkt("POINT (-90 -180)"));
        assertEquals("POINT (-90 -180)", udf.evaluate(args(wkb)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkb() throws Exception {
        udf.evaluate(args(new byte[]{0x01, 0x02, 0x03}));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test(expected = HiveException.class)
    public void testUnsupportedType() throws Exception {
        // 入参为 String 而非 byte[]/BytesWritable → HiveException
        udf.evaluate(args("POINT (2 4)"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StGeogfromwkbUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaByteArrayObjectInspector,
                PrimitiveObjectInspectorFactory.javaByteArrayObjectInspector
        });
    }
}
