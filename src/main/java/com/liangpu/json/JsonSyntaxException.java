package com.liangpu.json;

/**
 * 非法 JSON 文本异常（数据问题）。
 *
 * <p>design ADR-3「数据宽容、写法严格」：数据类异常由函数捕获后转为
 * NULL / UDTF 0 行，不抛给 Spark 任务（脏数据在 web 日志 / kafka 场景是常态）。</p>
 */
public class JsonSyntaxException extends RuntimeException {

    public JsonSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
