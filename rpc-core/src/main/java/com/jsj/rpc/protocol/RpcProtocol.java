package com.jsj.rpc.protocol;

import com.jsj.rpc.ChannelInfo;
import com.jsj.rpc.RpcFuture;
import com.jsj.rpc.RpcInvokeException;
import com.jsj.rpc.RpcMethodDetail;
import com.jsj.rpc.protocol.exception.BadSchemaException;
import com.jsj.rpc.protocol.exception.NotEnoughDataException;
import com.jsj.rpc.server.ServiceManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * header：magic_num(1 byte) | body_length(4 byte)
 * body: msg content
 *
 * @author jiangshenjie
 */
@Slf4j
@Setter
public class RpcProtocol implements Protocol {
    private static int FIXED_HEADER_LEN = 5;
    /**
     * 默认协议版本号(1 byte)
     */
    private static byte MAGIC_NUM = (byte) 0x00;
    private ServiceManager serviceManager = ServiceManager.getInstance();

    public RpcProtocol() {
    }

    public RpcProtocol(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public Packet parseHeader(ByteBuf in) throws BadSchemaException, NotEnoughDataException {
        if (in.readableBytes() < FIXED_HEADER_LEN) {
            throw new NotEnoughDataException();
        }
        in.markReaderIndex();
        byte magicNumber = in.readByte();
        int bodyLength = in.readInt();
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            throw new NotEnoughDataException();
        } else if (in.readableBytes() > bodyLength) {
            in.resetReaderIndex();
            throw new BadSchemaException();
        }
        ByteBuf bodyBuf = in.readRetainedSlice(bodyLength);
        return new RpcPacket(bodyBuf);
    }

    @Override
    public ByteBuf encodePacket(Packet packet) throws Exception {
        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer(2);
        ByteBuf headBuf = Unpooled.buffer(FIXED_HEADER_LEN);
        //protocol version
        headBuf.writeByte(MAGIC_NUM);
        if (packet == null) {
            //body length
            headBuf.writeInt(0);
            compositeByteBuf.addComponent(true, headBuf);
        } else {
            //body length
            ByteBuf bodyBuf = ((RpcPacket) packet).getBody();
            headBuf.writeInt(bodyBuf.readableBytes());
            compositeByteBuf.addComponent(true, headBuf);
            compositeByteBuf.addComponent(true, bodyBuf);
        }
        return compositeByteBuf;
    }

    @Override
    public Request decodeAsRequest(Packet packet) throws Exception {
        RpcMeta.RequestMeta requestMeta = RpcMeta.RequestMeta
                .parseFrom(((RpcPacket) packet).getBody().nioBuffer());
        RpcMethodDetail methodDetail =
                serviceManager.getService(requestMeta.getServiceName(), requestMeta.getMethodName());
        if (methodDetail == null) {
            String errMsg = String.format("rpc interface name: %s, method name: %s"
                    , requestMeta.getServiceName(), requestMeta.getMethodName());
            throw new NoSuchMethodException(errMsg);
        }
        //参数类型转换
        Class[] paramTypes = methodDetail.getMethod().getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Object param = requestMeta.getParams(i).unpack(paramTypes[i]);
            params[i] = param;
        }
        RpcRequest request = new RpcRequest();
        request.setRequestId(requestMeta.getRequestId());
        request.setServiceName(requestMeta.getServiceName());
        request.setMethodName(requestMeta.getMethodName());
        request.setParams(params);
        request.setMethod(methodDetail.getMethod());
        request.setTarget(methodDetail.getTarget());
        return request;
    }

    @Override
    public Response decodeAsResponse(Packet packet, ChannelInfo channelInfo) throws Exception {
        RpcMeta.ResponseMeta responseMeta = RpcMeta.ResponseMeta
                .parseFrom(((RpcPacket) packet).getBody().nioBuffer());
        RpcFuture<?> rpcFuture = channelInfo.getAndRemoveRpcFuture(responseMeta.getRequestId());
        Request request = rpcFuture.getRequest();
        Class returnType = request.getMethod().getReturnType();
        RpcResponse response = new RpcResponse();
        response.setRequestId(responseMeta.getRequestId());
        response.setRpcFuture(rpcFuture);
        if (responseMeta.getResult() != null) {
            response.setResult(responseMeta.getResult().unpack(returnType));
        }
        if (responseMeta.getErrMsg() != null && !"".equals(responseMeta.getErrMsg())) {
            response.setException(new RpcInvokeException(responseMeta.getErrMsg()));
        }
        return response;
    }
}
