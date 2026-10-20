package org.example

import com.sun.net.httpserver.HttpExchange
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Lê o corpo de um `application/x-www-form-urlencoded` e devolve os parâmetros
 * indexados pelo nome do campo.
 */
fun HttpExchange.formParams(): Map<String, List<String>> =
    requestBody.readBytes()
        .toString(StandardCharsets.UTF_8)
        .split("&")
        .filter { it.isNotBlank() }
        .groupBy({ it.substringBefore("=") }, { it.substringAfter("=") })
        .mapKeys { (key, _) -> URLDecoder.decode(key, StandardCharsets.UTF_8) }
        .mapValues { (_, values) -> values.map { URLDecoder.decode(it, StandardCharsets.UTF_8) } }
