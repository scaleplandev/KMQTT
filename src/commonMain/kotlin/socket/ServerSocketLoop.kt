package socket

import mqtt.broker.Broker
import mqtt.broker.cluster.ClusterConnection
import mqtt.broker.cluster.ClusterDiscoveryConnection
import socket.tls.TLSServerSocket

open class ServerSocketLoop(private val broker: Broker) {

    private val serverSocket = if (broker.tlsSettings == null) ServerSocket(broker) else TLSServerSocket(broker)

    fun run() {
        while (serverSocket.isRunning()) {
            serverSocket.select(250) { attachment, state ->
                when (attachment) {
                    is TCPEventHandler -> return@select handleEvent(attachment, state)
                    is ClusterDiscoveryConnection -> return@select handleDiscoveryEvent(attachment, state)
                    else -> return@select true
                }
            }
            broker.cleanUpOperations()
        }
    }

    private fun handleEvent(tcpEventHandler: TCPEventHandler, state: SocketState): Boolean {
        try {
            when (state) {
                SocketState.READ -> {
                    do {
                        val data = tcpEventHandler.read()
                        data?.let {
                            tcpEventHandler.dataReceived(it)
                        }
                    } while (data != null)
                }
                SocketState.WRITE -> {
                    tcpEventHandler.sendRemaining()
                }
            }
            return true
        } catch (e: SocketClosedException) {
            tcpEventHandler.closedGracefully()
            if (tcpEventHandler is ClusterConnection) {
                broker.removeClusterConnection(tcpEventHandler)
            }
        } catch (e: IOException) {
            tcpEventHandler.closedWithException()
            if (tcpEventHandler is ClusterConnection) {
                broker.removeClusterConnection(tcpEventHandler)
            }
        }
        return false
    }

    private fun handleDiscoveryEvent(connection: ClusterDiscoveryConnection, state: SocketState): Boolean {
        if (state == SocketState.READ) {
            connection.dataReceived()
        }
        return true
    }

    fun addClusterConnection(address: String): ClusterConnection? {
        return serverSocket.addClusterConnection(address)
    }

    fun stop() {
        serverSocket.close()
    }

    enum class SocketState {
        READ,
        WRITE
    }
}
