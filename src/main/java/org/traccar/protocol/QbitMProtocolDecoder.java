/*
 * Copyright 2025 Aaron Donnelly (support@complexus.uk)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.Protocol;
import org.traccar.helper.Checksum;
import org.traccar.helper.DateBuilder;
import org.traccar.model.Position;
import org.traccar.NetworkMessage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.TimeZone;

public class QbitMProtocolDecoder extends BaseProtocolDecoder {

    public QbitMProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private static final Logger logger = LoggerFactory.getLogger(QbitMProtocolDecoder.class);

    @Override
    protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        int header = buf.readUnsignedShort();
        if (header != 0x7878 && header != 0x7979) {
            return null;
        }

        int length = buf.readUnsignedByte();
        int protocolNumber = buf.readUnsignedByte();

        if (protocolNumber == 0x01) {
            // Login packet
            byte[] imeiBytes = new byte[8];
            buf.readBytes(imeiBytes);
            StringBuilder imei = new StringBuilder();
            for (byte b : imeiBytes) {
                imei.append(String.format("%02x", b));
            }

            getDeviceSession(channel, remoteAddress, imei.toString());

            buf.skipBytes(4); // Type ID + Timezone/Language
            int infoSn = buf.readUnsignedShort();
            buf.readUnsignedShort(); // CRC
            buf.readUnsignedShort(); // Stop bytes

            if (channel != null) {
                ByteBuf response = Unpooled.buffer();
                response.writeShort(0x7878);
                response.writeByte(5);
                response.writeByte(0x01);
                response.writeShort(infoSn);
                response.writeShort(Checksum.crc16(Checksum.CRC16_X25, response.nioBuffer(2, 4)));
                response.writeShort(0x0D0A);
                channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
            }

            return null;
        }

        if (protocolNumber == 0x36) {
            // Heartbeat packet
            getDeviceSession(channel, remoteAddress); // optional

            // Skip known fields (adjust if needed)
            buf.skipBytes(length - 5); // leave last 2 + 2 + 2 bytes

            int infoSn = buf.readUnsignedShort();
            buf.readUnsignedShort(); // CRC
            buf.readUnsignedShort(); // Stop bytes

            if (channel != null) {
                ByteBuf response = Unpooled.buffer();
                response.writeShort(0x7878);
                response.writeByte(5);
                response.writeByte(0x36);
                response.writeShort(infoSn);
                response.writeShort(Checksum.crc16(Checksum.CRC16_X25, response.nioBuffer(2, 4)));
                response.writeShort(0x0D0A);
                channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
            }

            return null;
        }

        if (protocolNumber == 0x8A) {
            // Time calibration packet
            int infoSn = buf.readUnsignedShort();
            buf.readUnsignedShort(); // CRC
            buf.readUnsignedShort(); // Stop bytes

            if (channel != null) {
                ByteBuf response = Unpooled.buffer();
                response.writeShort(0x7878);
                response.writeByte(11); // Length
                response.writeByte(0x8A);

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                response.writeByte(now.getYear() % 100);
                response.writeByte(now.getMonthValue());
                response.writeByte(now.getDayOfMonth());
                response.writeByte(now.getHour());
                response.writeByte(now.getMinute());
                response.writeByte(now.getSecond());

                response.writeShort(infoSn);
                response.writeShort(Checksum.crc16(Checksum.CRC16_X25, response.nioBuffer(2, 9)));
                response.writeShort(0x0D0A);

                channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
            }

            return null;
        }

        if (protocolNumber == 0x94) {
            // Information transfer packet
            int infoType = buf.readUnsignedByte();

            int contentLength = length - 1 - 2 - 2; // infoType + infoSn + CRC
            byte[] contentBytes = new byte[contentLength];
            buf.readBytes(contentBytes);
            String content = new String(contentBytes, StandardCharsets.US_ASCII);

            int infoSn = buf.readUnsignedShort();
            logger.info("QBit M - Received info transfer packet with SN: {}", infoSn);
            buf.readUnsignedShort(); // CRC
            buf.readUnsignedShort(); // Stop bytes

            getDeviceSession(channel, remoteAddress);

            Position position = new Position(getProtocolName());
            position.set(Position.KEY_RESULT, "InfoType: " + infoType + ", Content: " + content);
            return position;
        }

        if (protocolNumber == 0x38 || protocolNumber == 0xA0) {
            var deviceSession = getDeviceSession(channel, remoteAddress);
            if (deviceSession == null) {
                return null;
            }

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            DateBuilder dateBuilder = new DateBuilder(TimeZone.getTimeZone("UTC"));
            dateBuilder.setDate(
                    buf.readUnsignedByte(), // year
                    buf.readUnsignedByte(), // month
                    buf.readUnsignedByte() // day
            );
            dateBuilder.setTime(
                    buf.readUnsignedByte(), // hour
                    buf.readUnsignedByte(), // minute
                    buf.readUnsignedByte() // second
            );
            position.setTime(dateBuilder.getDate());

            buf.readUnsignedByte(); // GPS info length + satellite count

            position.setLatitude(buf.readUnsignedInt() / 1800000.0);
            position.setLongitude(buf.readUnsignedInt() / 1800000.0);
            position.setSpeed(buf.readUnsignedByte());

            int courseStatus = buf.readUnsignedShort();
            position.setCourse(courseStatus & 0x03FF);
            position.set(Position.KEY_IGNITION, (courseStatus & 0x4000) != 0);
            position.setValid((courseStatus & 0x2000) != 0);

            // Skip remaining bytes if needed
            buf.skipBytes(length - 12);

            buf.readUnsignedShort(); // Info SN
            buf.readUnsignedShort(); // CRC
            buf.readUnsignedShort(); // Stop bytes

            int mcc = buf.readUnsignedShort();
            boolean mncIsTwoBytes = (mcc & 0x8000) != 0;

            int mnc = mncIsTwoBytes ? buf.readUnsignedShort() : buf.readUnsignedByte();
            int lac = buf.readUnsignedShort(); // or readInt() if 4 bytes
            long cellId = buf.readUnsignedInt(); // or readLong() if 8 bytes
            int rssi = buf.readUnsignedByte(); // or readUnsignedShort() if 2 bytes

            position.set("mcc", mcc & 0x7FFF); // remove MSB
            position.set("mnc", mnc);
            position.set("lac", lac);
            position.set("cellId", cellId);
            position.set("rssi", rssi);

            return position;
        }

        return null;
    }
}