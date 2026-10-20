package org.example

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Resposta HTTP (mínima) devolvida por uma rota.
 */
data class Response(
    val body: String,
    val status: Int = 200,
    val contentType: String = "text/html; charset=utf-8"
)

/**
 * Servidor HTTP mínimo, construído sobre o `com.sun.net.httpserver` que já vem
 * na JDK — sem dependências extras.
 *
 * Basta declarar as rotas e chamar [start]:
 *
 * ```
 * MiniHttpServer(8080)
 *     .route("/") { Response("<h1>Olá!</h1>") }
 *     .start()
 * ```
 */
class MiniHttpServer(private val port: Int) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    private val routes = mutableMapOf<String, (HttpExchange) -> Response>()

    /** Registra uma rota. O caminho é comparado exatamente, semQueryString. */
    fun route(path: String, handler: (HttpExchange) -> Response): MiniHttpServer = apply {
        routes[path] = handler
    }

    /** Sobe o servidor (bloqueante). */
    fun start(): MiniHttpServer = apply {
        server.createContext("/") { exchange ->
            val handler = routes[exchange.requestURI.path]
            val response = when {
                handler == null -> Response("<h1>404</h1><p>Rota não encontrada.</p>", status = 404)
                exchange.requestMethod != "GET" -> Response("<h1>405</h1><p>Método não permitido.</p>", status = 405)
                else -> handler(exchange)
            }

            val body = response.body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", response.contentType)
            exchange.sendResponseHeaders(response.status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        server.executor = Executors.newFixedThreadPool(4) { task ->
            Thread(task).apply { isDaemon = true }
        }
        server.start()
        println("[http] Servidor no ar em http://localhost:$port")
    }

    /** Derruba o servidor. */
    fun stop() = server.stop(0)
}
