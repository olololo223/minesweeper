package com.example.minesweeper.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public class GameMessageCodec {

    /**
     * Общий ObjectMapper. Потокобезопасен.
     *
     * FAIL_ON_EMPTY_BEANS выключен: у запросов без параметров (например,
     * GetFriendsRequest) нет полей, и по умолчанию Jackson отказывается
     * такие объекты сериализовать — сообщение молча не уходило в сеть,
     * а клиент ждал ответа до таймаута.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    // Максимум 1 МБ payload — защита от мусорных кадров
    private static final int MAX_PAYLOAD = 1_000_000;

    /** GameMessage → байты. */
    public static class Encoder extends MessageToByteEncoder<GameMessage> {
        @Override
        protected void encode(ChannelHandlerContext ctx,
                              GameMessage msg,
                              ByteBuf out) throws Exception {
            byte[] payload;
            if (msg.getPayload() == null) {
                payload = new byte[0];
            } else {
                payload = MAPPER.writeValueAsBytes(msg.getPayload());
            }
            out.writeInt(payload.length);
            out.writeByte(msg.getType().ordinal());
            out.writeBytes(payload);
        }
    }

    /** Байты → GameMessage. */
    public static class Decoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx,
                              ByteBuf in,
                              List<Object> out) throws Exception {
            // Минимум: 4 (length) + 1 (type)
            if (in.readableBytes() < 5) return;

            in.markReaderIndex();
            int length = in.readInt();
            if (length < 0 || length > MAX_PAYLOAD) {
                throw new IllegalStateException("Некорректная длина payload: " + length);
            }
            if (in.readableBytes() < length + 1) {
                in.resetReaderIndex();
                return;
            }

            byte typeOrdinal = in.readByte();
            byte[] payload = new byte[length];
            in.readBytes(payload);

            MessageType[] types = MessageType.values();
            if (typeOrdinal < 0 || typeOrdinal >= types.length) {
                throw new IllegalStateException("Неизвестный тип сообщения: " + typeOrdinal);
            }
            MessageType type = types[typeOrdinal];

            Object obj = null;
            Class<?> payloadClass = type.getPayloadClass();
            if (payloadClass != null && length > 0) {
                obj = MAPPER.readValue(payload, payloadClass);
            }

            out.add(new GameMessage(type, obj));
        }
    }
}