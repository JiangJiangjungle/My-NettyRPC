package com.jsj.rpc.protocol;

import com.jsj.rpc.ChannelInfo;
import com.jsj.rpc.exception.BadSchemaException;
import com.jsj.rpc.exception.DecodeException;
import com.jsj.rpc.exception.NotEnoughDataException;
import io.netty.buffer.ByteBuf;

/**
 * @author jiangshenjie
 */
public interface Protocol {

    Packet createPacket(ByteBuf data);

    Packet createPacket(byte[] data);

    Request createRequest();

    Response createResponse();

    /**
     * 将Packet编码成协议报文
     *
     * @param packet 消息包
     * @return ByteBuf
     * @throws Exception
     */
    ByteBuf encodePacket(Packet packet);

    /**
     * 解析报文的header，将消息实体封装成Packet对象
     *
     * @param in
     * @return
     * @throws BadSchemaException
     * @throws NotEnoughDataException
     */
    Packet parseHeaderAndPackage(ByteBuf in) throws BadSchemaException, NotEnoughDataException;

    /**************** 仅Rpc Server需要实现的函数 *******************/

    /**
     * 从packet中获取RpcRequest对象
     *
     * @param packet
     * @return
     * @throws DecodeException
     */
    Request decodeAsRequest(Packet packet) throws DecodeException;

    /**************** 仅Rpc Client需要实现的函数 *******************/

    /**
     * 从Packet反序列化出RpcResponse对象
     *
     * @param packet
     * @param channelInfo
     * @return
     * @throws DecodeException
     */
    Response decodeAsResponse(Packet packet, ChannelInfo channelInfo) throws DecodeException;
}
