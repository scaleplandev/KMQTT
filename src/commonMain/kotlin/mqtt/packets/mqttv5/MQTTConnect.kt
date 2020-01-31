package mqtt.packets.mqttv5

import mqtt.MQTTException
import mqtt.packets.MQTTControlPacketType
import mqtt.packets.Qos
import socket.streams.ByteArrayInputStream
import socket.streams.ByteArrayOutputStream

class MQTTConnect(
    val protocolName: String,
    val protocolVersion: Int,
    val connectFlags: ConnectFlags,
    val keepAlive: Int,
    val clientID: String = "",
    val properties: MQTTProperties = MQTTProperties(),
    val willProperties: MQTTProperties? = null,
    val willTopic: String? = null,
    val willPayload: UByteArray? = null,
    val userName: String? = null,
    val password: UByteArray? = null
) : MQTTPacket {

    companion object : MQTTDeserializer {
        private val validProperties = listOf(
            Property.SESSION_EXPIRY_INTERVAL,
            Property.AUTHENTICATION_METHOD,
            Property.AUTHENTICATION_DATA,
            Property.REQUEST_PROBLEM_INFORMATION,
            Property.REQUEST_RESPONSE_INFORMATION,
            Property.RECEIVE_MAXIMUM,
            Property.TOPIC_ALIAS_MAXIMUM,
            Property.USER_PROPERTY,
            Property.MAXIMUM_PACKET_SIZE
        )

        private val validWillProperties = listOf(
            Property.PAYLOAD_FORMAT_INDICATOR,
            Property.MESSAGE_EXPIRY_INTERVAL,
            Property.CONTENT_TYPE,
            Property.RESPONSE_TOPIC,
            Property.CORRELATION_DATA,
            Property.WILL_DELAY_INTERVAL,
            Property.USER_PROPERTY
        )

        data class ConnectFlags(
            val userNameFlag: Boolean,
            val passwordFlag: Boolean,
            val willRetain: Boolean,
            val willQos: Qos,
            val willFlag: Boolean,
            val cleanStart: Boolean,
            val reserved: Boolean
        ) {
            fun toByte(): UInt {
                val flags = (((if (userNameFlag) 1 else 0) shl 7) and 0x80) or
                        (((if (passwordFlag) 1 else 0) shl 6) and 0x40) or
                        (((if (willRetain) 1 else 0) shl 5) and 0x20) or
                        (((willQos.value) shl 3) and 0x18) or
                        (((if (willFlag) 1 else 0) shl 2) and 0x4) or
                        (((if (cleanStart) 1 else 0) shl 1) and 0x2) or
                        ((if (reserved) 1 else 0) and 0x1)
                return flags.toUInt()
            }
        }

        private fun connectFlags(byte: Int): ConnectFlags {
            val reserved = (byte and 1) == 1
            if (reserved)
                throw MQTTException(ReasonCode.MALFORMED_PACKET)
            val willFlag = ((byte shr 2) and 1) == 1
            val willQos = ((byte shr 4) and 1) or ((byte shl 3) and 1)
            val willRetain = ((byte shr 5) and 1) == 1
            if (willFlag) {
                if (willQos == 3)
                    throw MQTTException(ReasonCode.MALFORMED_PACKET)
            } else {
                if (willQos != 0)
                    throw MQTTException(ReasonCode.MALFORMED_PACKET)
                if (willRetain)
                    throw MQTTException(ReasonCode.MALFORMED_PACKET)
            }

            return ConnectFlags(
                ((byte shr 7) and 1) == 1,
                ((byte shr 6) and 1) == 1,
                willRetain,
                Qos.valueOf(willQos)!!,
                willFlag,
                ((byte shr 1) and 1) == 1,
                reserved
            )
        }

        override fun fromByteArray(flags: Int, data: UByteArray): MQTTConnect {
            checkFlags(flags)

            val inStream = ByteArrayInputStream(data)
            val protocolName = inStream.readUTF8String()
            if (protocolName != "MQTT")
                throw MQTTException(ReasonCode.UNSUPPORTED_PROTOCOL_VERSION)
            val protocolVersion = inStream.read().toInt()
            if (protocolVersion != 5)
                throw MQTTException(ReasonCode.UNSUPPORTED_PROTOCOL_VERSION)

            val connectFlags =
                connectFlags(inStream.read().toInt())
            val keepAlive = inStream.read2BytesInt()

            val properties = inStream.deserializeProperties(validProperties)
            if (properties.authenticationData != null && properties.authenticationMethod == null)
                throw MQTTException(ReasonCode.PROTOCOL_ERROR)

            // Payload
            val clientID = inStream.readUTF8String()

            val willProperties =
                if (connectFlags.willFlag) inStream.deserializeProperties(validWillProperties) else null
            val willTopic = if (connectFlags.willFlag) inStream.readUTF8String() else null
            val willPayload = if (connectFlags.willFlag) inStream.readBinaryData() else null
            val userName = if (connectFlags.userNameFlag) inStream.readUTF8String() else null
            val password = if (connectFlags.passwordFlag) inStream.readBinaryData() else null

            return MQTTConnect(
                protocolName,
                protocolVersion,
                connectFlags,
                keepAlive.toInt(),
                clientID,
                properties,
                willProperties,
                willTopic,
                willPayload,
                userName,
                password
            )
        }
    }

    override fun toByteArray(): UByteArray {
        val outStream = ByteArrayOutputStream()
        outStream.writeUTF8String("MQTT")
        outStream.writeByte(5u)
        outStream.writeByte(connectFlags.toByte())
        outStream.write2BytesInt(keepAlive.toUInt())
        outStream.write(properties.serializeProperties(validProperties))

        // Payload
        outStream.writeUTF8String(clientID)
        if (connectFlags.willFlag) {
            try {
                outStream.write(willProperties!!.serializeProperties(validWillProperties))
                outStream.writeUTF8String(willTopic!!)
                outStream.writeBinaryData(willPayload!!)
                outStream.writeUTF8String(userName!!)
                outStream.writeBinaryData(password!!)
            } catch (e: NullPointerException) {
                throw MQTTException(ReasonCode.MALFORMED_PACKET)
            }
        }

        return outStream.wrapWithFixedHeader(MQTTControlPacketType.CONNECT, 0)
    }
}
