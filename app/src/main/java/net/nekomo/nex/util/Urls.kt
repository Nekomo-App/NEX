package net.nekomo.nex.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Urls {
    sealed interface Validation {
        data class Valid(val url: String) : Validation
        data class Invalid(val reason: String) : Validation
    }

    /** Validates a user supplied repository/API URL. Plain http is refused unless explicitly allowed. */
    fun validate(input: String, allowHttp: Boolean = false): Validation {
        val t = input.trim()
        if (t.isEmpty()) return Validation.Invalid("Enter a URL")
        if (t.any { it.isWhitespace() }) return Validation.Invalid("URLs can't contain spaces")
        val withScheme = if ("://" !in t) "https://$t" else t
        val parsed = withScheme.toHttpUrlOrNull() ?: return Validation.Invalid("Only http(s) URLs are supported")
        if (parsed.host.isBlank() || !(parsed.host.contains('.') || parsed.host == "localhost" || parsed.host.all { it.isDigit() || it == '.' || it == ':' }))
            return Validation.Invalid("\"${parsed.host}\" is not a valid host")
        if (parsed.scheme == "http" && !allowHttp)
            return Validation.Invalid("Insecure http:// URLs are blocked. Use https:// or allow http in Settings → Security.")
        return Validation.Valid(parsed.toString())
    }

    fun host(url: String?): String = url?.toHttpUrlOrNull()?.host ?: url.orEmpty()

    /** Resolves [path] against the directory of [base]. Absolute URLs are returned as is. */
    fun resolve(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val b = base.toHttpUrlOrNull() ?: return path
        return b.resolve(path)?.toString() ?: path
    }

    /** Best-effort "view source" link: github raw URLs map back to the repository page. */
    fun browsePage(url: String): String {
        val u = url.toHttpUrlOrNull() ?: return url
        if (u.host == "raw.githubusercontent.com" && u.pathSegments.size >= 2)
            return "https://github.com/${u.pathSegments[0]}/${u.pathSegments[1]}"
        return url.substringBeforeLast('/')
    }
}
