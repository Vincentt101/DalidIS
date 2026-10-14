package com.dalidis.payannouncer

/**
 * Extracts an incoming payment (amount + sender) from notification text.
 *
 * Pure Kotlin with no Android dependencies so it can be unit tested on the JVM.
 * To support another app's wording, add a [Rule] to [rules].
 */
object PaymentParser {

    data class Payment(val amount: String, val sender: String) {
        fun toSpeech(): String = "You have received $amount from $sender."
    }

    private const val SYMBOLS = "$€£¥₹₱₩₦₺₽"

    // "1,234.56", "1 234.56", "350", "350.5"
    private const val NUMBER = """\d{1,3}(?:[,\s]\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?"""

    // Optional ISO code before, optional currency symbol, the number, optional ISO code after.
    // e.g. "$350.00", "$350.00 USD", "USD 350", "PHP 1,500.00", "€ 20", "350.00"
    private const val AMOUNT =
        """((?:\b[A-Z]{3}\s?)?(?:[A-Z]{0,2}[$SYMBOLS]\s?)?(?:$NUMBER)(?:\s?[A-Z]{3}\b)?)"""

    // A sender that comes *before* the verb: cannot cross sentence punctuation, so
    // "You got money. John Doe sent you $5" yields "John Doe".
    private const val SENDER_BEFORE = """([^.!?:;\n]{1,80}?)"""

    // A sender that comes *after* "from": stops at punctuation, a line end, or a
    // trailing clause like " on 12 Oct", " via Wise", " to your account".
    private const val SENDER_AFTER =
        """(.{1,80}?)(?=\s+(?:on|via|for|into|to|at|with|ref|reference|using)\b|[.!,;(\n]|$)"""

    private class Rule(pattern: String, val senderGroup: Int, val amountGroup: Int) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private val rules = listOf(
        // "John Doe sent you $350.00"  /  "John Doe has sent you $350.00 USD"
        Rule("""$SENDER_BEFORE\s+(?:has\s+|just\s+)?sent\s+you\s+$AMOUNT""", 1, 2),
        // "John Doe paid you $350.00"
        Rule("""$SENDER_BEFORE\s+(?:has\s+|just\s+)?paid\s+you\s+$AMOUNT""", 1, 2),
        // "John Doe sent $350.00 to you"
        Rule("""$SENDER_BEFORE\s+(?:has\s+)?sent\s+$AMOUNT\s+to\s+you""", 1, 2),
        // "You received $350.00 from John Doe" / "You've received ..." / "Received ..."
        Rule("""received\s+$AMOUNT\s+from\s+$SENDER_AFTER""", 2, 1),
        // "You got $350 from John Doe"
        Rule("""got\s+$AMOUNT\s+from\s+$SENDER_AFTER""", 2, 1),
        // "Payment of $350.00 from John Doe" / "Transfer of ..." / "Deposit of ..."
        Rule("""(?:payment|transfer|deposit|credit)\s+of\s+$AMOUNT\s+(?:received\s+)?from\s+$SENDER_AFTER""", 2, 1),
        // "$350.00 received from John Doe" / "$350.00 from John Doe"
        Rule("""$AMOUNT\s+(?:has\s+been\s+|was\s+)?(?:received\s+|credited\s+|deposited\s+)?from\s+$SENDER_AFTER""", 2, 1),
    )

    /**
     * Tries each candidate string (e.g. big text, text, title) in order and returns the
     * first successful parse, or null if nothing looks like an incoming payment.
     */
    fun parse(candidates: List<String?>): Payment? {
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) continue
            parse(candidate)?.let { return it }
        }
        return null
    }

    fun parse(text: String): Payment? {
        val normalized = text.replace(' ', ' ').replace(Regex("""[ \t]+"""), " ")
        for (rule in rules) {
            for (match in rule.regex.findAll(normalized)) {
                val amount = normalizeAmount(match.groupValues[rule.amountGroup]) ?: continue
                val sender = cleanSender(match.groupValues[rule.senderGroup]) ?: continue
                return Payment(amount, sender)
            }
        }
        return null
    }

    private val codeToSymbol = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
        "INR" to "₹", "PHP" to "₱", "KRW" to "₩", "NGN" to "₦",
    )

    /** Turns "$350.00 USD" into "$350", "PHP 1,500.50" into "₱1,500.50", "350.00 CAD" into "350 CAD". */
    internal fun normalizeAmount(raw: String): String? {
        val s = raw.trim()
        val number = Regex(NUMBER).find(s)?.value ?: return null
        val before = s.substring(0, s.indexOf(number))
        val after = s.substring(s.indexOf(number) + number.length)

        val symbol = before.firstOrNull { it in SYMBOLS }?.toString()
        val code = Regex("""\b([A-Z]{3})\b""").find(before + " " + after)?.groupValues?.get(1)

        // A bare number with no currency marker and no decimals is too ambiguous (could be a
        // count, a date, an order number), so only accept it if it has cents.
        if (symbol == null && code == null && !number.contains('.')) return null

        val cleanNumber = number.replace(" ", ",").removeSuffix(".00").let {
            if (Regex("""\.\d$""").containsMatchIn(it)) it + "0" else it
        }

        return when {
            symbol != null && code != null && codeToSymbol[code] != symbol ->
                "$symbol$cleanNumber $code"
            symbol != null -> "$symbol$cleanNumber"
            code != null && codeToSymbol.containsKey(code) -> "${codeToSymbol[code]}$cleanNumber"
            code != null -> "$cleanNumber $code"
            else -> cleanNumber
        }
    }

    private val notASender = setOf("you", "someone", "a", "an", "the", "we", "i")

    internal fun cleanSender(raw: String): String? {
        val s = raw.trim()
            .removePrefix("from ").removePrefix("From ")
            .trim(' ', '-', '–', '—', ',', '"', '\'', '*')
            .trim()
        if (s.isEmpty() || s.length > 60) return null
        if (s.none { it.isLetter() }) return null
        if (s.lowercase() in notASender) return null
        return s
    }
}
