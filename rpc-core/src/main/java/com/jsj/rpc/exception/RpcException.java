package com.jsj.rpc.exception;

/**
 * @author jiangshenjie
 */
public class RpcException extends RuntimeException {
    private int code;

    public RpcException(RpcExceptionType rpcExceptionType) {
        this(rpcExceptionType.getMessage(), rpcExceptionType.getCode());
    }

    public RpcException(int code) {
        this.code = code;
    }

    public RpcException(String message) {
        super(message);
    }

    public RpcException(Throwable cause) {
        super(cause);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcException(String message, int code) {
        super(message);
        this.code = code;
    }

    public RpcException(String message, Throwable cause, int code) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
