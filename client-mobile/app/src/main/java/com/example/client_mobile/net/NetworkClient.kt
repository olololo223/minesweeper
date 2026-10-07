package com.example.client_mobile.net

import android.util.Log
import com.example.client_mobile.protocol.GameMessage
import com.example.client_mobile.protocol.MessageType
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Простой TCP-клиент под наш протокол.
 * Работает в отдельном потоке, колбэки — через Coroutines.
 */
class NetworkClient(
    private val host: String,
    private val port: Int
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private val mapper = ObjectMapper()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Ожидающие ответы: тип → continuation
    private val pending = ConcurrentHashMap<MessageType, CompletableDeferred<Any>>()

    // Слушатели push'ей
    private val listeners = mutableListOf<(GameMessage) -> Unit>()

    /** Подключение. Бросает исключение при ошибке. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        Log.d("NetClient", "Connecting to $host:$port")
        val addr = when (host) {
            "10.0.2.2" -> byteArrayOf(10, 0, 2, 2).let { java.net.InetAddress.getByAddress(it) }
            else -> java.net.InetAddress.getByName(host)
        }
        Log.d("NetClient", "Resolved to ${addr.hostAddress}")

        socket = Socket().apply {
            connect(java.net.InetSocketAddress(addr, port), 5000)
            tcpNoDelay = true
        }
        Log.d("NetClient", "Connected successfully")

        input = DataInputStream(socket!!.getInputStream())
        output = DataOutputStream(socket!!.getOutputStream())
        scope.launch { readLoop() }
    }

    /** Отправить запрос и дождаться ответа заданного типа. */
    suspend fun <T> request(
        requestType: MessageType,
        payload: Any?,
        responseType: MessageType,
        responseClass: Class<T>
    ): T = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Any>()
        pending[responseType] = deferred
        send(requestType, payload)

        // Таймаут 5 сек, чтобы не висеть вечно
        val result = withTimeout(5000) { deferred.await() }
        @Suppress("UNCHECKED_CAST")
        result as T
    }

    /** Отправить сообщение без ожидания ответа (fire-and-forget). */
    fun send(type: MessageType, payload: Any?) {
        synchronized(this) {
            val json = if (payload == null) ByteArray(0)
            else mapper.writeValueAsBytes(payload)
            output!!.writeInt(json.size)
            output!!.writeByte(type.ordinal)
            output!!.write(json)
            output!!.flush()
        }
    }

    /** Добавить слушателя push-сообщений. */
    fun addListener(l: (GameMessage) -> Unit) {
        listeners.add(l)
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        scope.cancel()
    }

    // ---- Внутреннее ----

    private fun readLoop() {
        try {
            while (true) {
                val length = input!!.readInt()
                if (length < 0 || length > 1_000_000) error("Bad frame length: $length")
                val typeOrdinal = input!!.readByte().toInt()
                val payload = ByteArray(length).also { input!!.readFully(it) }

                val type = MessageType.values()[typeOrdinal]
                val payloadObj: Any? =
                    if (length == 0 || type.payloadClass == null) null
                    else mapper.readValue(payload, type.payloadClass)

                val msg = GameMessage(type, payloadObj)

                // Завершаем ожидающий future, если он есть
                val deferred = pending.remove(type)
                if (deferred != null) deferred.complete(payloadObj ?: Unit)

                // Уведомляем слушателей
                listeners.forEach { it(msg) }
            }
        } catch (e: Exception) {
            Log.e("NetClient", "Read loop died", e)
            pending.values.forEach { it.completeExceptionally(e) }
            pending.clear()
        }
    }
}