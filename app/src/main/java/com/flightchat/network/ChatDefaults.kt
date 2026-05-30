package com.flightchat.network

object ChatDefaults {
    const val DEFAULT_SERVER_PORT = 45655
    private const val LEGACY_DEFAULT_SERVER_PORT = 5555

    fun normalizeServerPort(port: Int): Int {
        return if (port == LEGACY_DEFAULT_SERVER_PORT) {
            DEFAULT_SERVER_PORT
        } else {
            port
        }
    }
}
