package io.peekandpoke.utils

fun String.sha256Hex(): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val hash = md.digest(this.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(hash.size * 2)
    for (b in hash) sb.append("%02x".format(b))
    return sb.toString()
}
