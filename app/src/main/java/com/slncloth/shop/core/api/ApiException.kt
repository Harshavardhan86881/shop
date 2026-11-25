package com.slncloth.shop.core.api

/**
 * Error raised by the server and mapped to a safe JSON error body.
 * Messages are deliberately generic and never include secret codes, PINs or tokens.
 */
class ApiException(
    val status: Int,
    val code: String,
    message: String,
) : RuntimeException(message) {

    companion object {
        fun badRequest(message: String) = ApiException(400, "INVALID_REQUEST", message)
        fun unauthorized(message: String = "Sign in required") = ApiException(401, "UNAUTHORIZED", message)
        fun forbidden(message: String = "Owner access required") = ApiException(403, "FORBIDDEN", message)
        fun notFound(message: String = "Not found") = ApiException(404, "NOT_FOUND", message)
        fun conflict(message: String, code: String = "CONFLICT") = ApiException(409, code, message)
        fun unprocessable(message: String, code: String) = ApiException(422, code, message)
        fun tooManyRequests(message: String) = ApiException(429, "RATE_LIMITED", message)
        fun server(message: String = "The server could not complete the request") =
            ApiException(500, "SERVER_ERROR", message)

        /** Used by the client when no HTTP response was received at all. */
        fun network(message: String = "Could not reach the shop server") =
            ApiException(0, "NETWORK_ERROR", message)

        fun client(message: String, code: String = "CLIENT_ERROR") =
            ApiException(0, code, message)
    }
}
