package com.liangpu.help;

import com.liangpu.help.LpudfFunction.Kind;
import org.apache.spark.sql.SparkSessionExtensions;
import org.apache.spark.sql.catalyst.FunctionIdentifier;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.ExpressionInfo;
import org.apache.spark.sql.hive.HiveGenericUDTF;
import org.apache.spark.sql.hive.HiveGenericUDF;
import org.apache.spark.sql.hive.HiveShim;
import org.apache.spark.sql.hive.HiveUDAFFunction;
import scala.Function1;
import scala.Option;
import scala.Tuple3;
import scala.collection.Seq;

/**
 * spark.sql.extensions 入口（迭代 3：desc function 帮助信息）。
 *
 * <p>集群配置一行即可：{@code spark.sql.extensions=com.liangpu.help.LpudfExtensions}。
 * 每个 SparkSession 启动时，将 12 个 lpudf 函数以带完整 {@link ExpressionInfo} 的方式注入
 * {@code SimpleFunctionRegistry}（POC 实测：该路径下 {@code DESC FUNCTION} 显示注入的帮助文本，
 * 而 hive {@code CREATE FUNCTION} 注册路径的 usage 硬编码为 null）。</p>
 *
 * <p>builder 使用 Spark 内置 Hive 包装类（行为与 hive 注册一致）：标量
 * {@link HiveGenericUDF}、聚合 {@link HiveUDAFFunction}、表函数 {@link HiveGenericUDTF}。</p>
 */
public class LpudfExtensions implements Function1<SparkSessionExtensions, Object> {

    @Override
    public Object apply(SparkSessionExtensions ext) {
        for (LpudfFunction f : LpudfFunctionRegistry.ALL) {
            ext.injectFunction(new Tuple3<FunctionIdentifier, ExpressionInfo,
                    Function1<Seq<Expression>, Expression>>(
                    new FunctionIdentifier(f.name, Option.apply(f.database)),
                    new ExpressionInfo(f.className, f.database, f.name, f.usage, f.arguments),
                    builderFor(f)));
        }
        return null;
    }

    private static Function1<Seq<Expression>, Expression> builderFor(final LpudfFunction f) {
        return new Function1<Seq<Expression>, Expression>() {
            @Override
            public Expression apply(Seq<Expression> children) {
                HiveShim.HiveFunctionWrapper wrapper =
                        new HiveShim.HiveFunctionWrapper(f.className, null, null);
                switch (f.kind) {
                    case UDTF:
                        return new HiveGenericUDTF(f.name, wrapper, children);
                    case UDAF:
                        return new HiveUDAFFunction(f.name, wrapper, children, false, 0, 0);
                    default:
                        return new HiveGenericUDF(f.name, wrapper, children);
                }
            }
        };
    }
}
