package com.liangpu.json;

/**
 * JSONPath 语法非法异常（SQL 写法问题）。
 *
 * <p>design ADR-3「数据宽容、写法严格」：path 是 SQL 书写的一部分，语法错误必须
 * 立即暴露（直接抛出，Spark 任务失败），与 MaxCompute 报错行为对齐。</p>
 */
public class JsonPathException extends RuntimeException {

    public JsonPathException(String message) {
        super(message);
    }

    public JsonPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
