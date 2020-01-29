package mqtt.packets

import mqtt.packets.mqttv5.MQTTConnect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMQTTConnect {

    private val array = ubyteArrayOf(0x10u, 0x0Du, 0x00u, 0x04u, 0x4Du, 0x51u, 0x54u, 0x54u, 0x05u, 0x02u, 0x00u, 0x3Cu, 0x00u, 0x00u, 0x00u)
    private val packet = MQTTConnect(
        "MQTT",
        5,
        MQTTConnect.Companion.ConnectFlags(
            false,
            false,
            false,
            Qos.AT_MOST_ONCE,
            false,
            true,
            false
        ),
        60
    )

    @Test
    fun testToByteArray() {
        assertTrue(array.contentEquals(packet.toByteArray()))
    }

    @Test
    fun testFromByteArray() {
        val result = MQTTConnect.fromByteArray(0, array.copyOfRange(2, array.size))
        assertEquals(packet.protocolName, result.protocolName)
        assertEquals(packet.protocolVersion, result.protocolVersion)
        assertEquals(packet.keepAlive, result.keepAlive)
    }
}
