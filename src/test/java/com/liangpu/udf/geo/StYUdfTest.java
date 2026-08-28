package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_y 单元测试：取点纬度；非 POINT 报错；NULL 传播。
 */
public class StYUdfTest {

    private StYUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StYUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }

    private GenericUDF.DeferredObject[] args(Object... values) {
        GenericUDF.DeferredObject[] result = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new GenericUDF.DeferredJavaObject(values[i]);
        }
        return result;
    }

    @Test
    public void testNormalPoint() throws Exception {
        assertEquals(4.0, (Double) udf.evaluate(args("POINT (2 4)")), 1e-9);
    }

    @Test
    public void testNegativeCoord() throws Exception {
        assertEquals(-90.0, (Double) udf.evaluate(args("POINT (-180 -90)")), 1e-9);
    }

    @Test
    public void testDecimalCoord() throws Exception {
        assertEquals(39.9042, (Double) udf.evaluate(args("POINT (116.4074 39.9042)")), 1e-9);
    }

    @Test(expected = HiveException.class)
    public void testNotPointLineString() throws Exception {
        udf.evaluate(args("LINESTRING (2 4, 3 5)"));
    }

    @Test(expected = HiveException.class)
    public void testNotPointPolygon() throws Exception {
        udf.evaluate(args("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))"));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StYUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
