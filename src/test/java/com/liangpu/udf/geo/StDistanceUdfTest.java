package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_distance 单元测试：两地理对象最短球面距离；NULL/空 → NULL；锚点校验。
 */
public class StDistanceUdfTest {

    private StDistanceUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StDistanceUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
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
    public void testAnchorMcDoc() throws Exception {
        // MC 文档锚点：ST_DISTANCE(ST_GEOGPOINT(0,0), ST_GEOGPOINT(1,1)) ≈ 157249.63
        double d = (Double) udf.evaluate(args("POINT (0 0)", "POINT (1 1)"));
        assertEquals(157249.63, d, 100);
    }

    @Test
    public void testSamePointZero() throws Exception {
        double d = (Double) udf.evaluate(args("POINT (30 60)", "POINT (30 60)"));
        assertEquals(0.0, d, 1e-6);
    }

    @Test
    public void testEquatorOneDegree() throws Exception {
        double d = (Double) udf.evaluate(args("POINT (0 0)", "POINT (1 0)"));
        assertEquals(111195, d, 100);
    }

    @Test
    public void testNegativeCoords() throws Exception {
        double d = (Double) udf.evaluate(args("POINT (-180 -90)", "POINT (0 0)"));
        // 球心角 90° → R·π/2 ≈ 10007543m
        assertEquals(10007543, d, 10000);
    }

    @Test
    public void testEmptyGeometry() throws Exception {
        assertNull(udf.evaluate(args("POINT EMPTY", "POINT (0 0)")));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args(null, "POINT (0 0)")));
        assertNull(udf.evaluate(args("POINT (0 0)", null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT", "POINT (0 0)"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StDistanceUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
