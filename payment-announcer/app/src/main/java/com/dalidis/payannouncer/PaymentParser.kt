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
    // e.g. "$350.00", "$350.00 USD", "USD 350", "PHP 1,500.00", "Php500", "P500.00" (peso),
    // "€ 20", "350.00"
    private const val AMOUNT =
        """((?:\b[A-Z]{3}\s?)?(?:[A-Z]{0,2}[$SYMBOLS]\s?|\bP\s?(?=\d))?(?:$NUMBER)(?:\s?[A-Z]{3}\b)?)"""

    // Words that can sit between the amount and "from":
    // "PHP 500.00 of GCash from …", "₱1,000.00 via InstaPay from …", "₱200 thru PESONet from …"
    private const val MIDDLE = """(?:(?:of|via|thru|through|using)\s+[\w-]+\s+)*"""

    // A sender that comes *before* the verb: cannot cross sentence punctuation, so
    // "You got money. John Doe sent you $5" yields "John Doe".
    private const val SENDER_BEFORE = """([^.!?:;\n]{1,80}?)"""

    // A sender that comes *after* "from": stops at punctuation, a line end, or a
    // trailing clause like " on 12 Oct", " via Wise", " to your account".
    // Also stops at "w/ 0917…" / a bare mobile or account number, as in GCash notifications.
    private const val SENDER_AFTER =
        """(.{1,80}?)(?=\s+(?:on|via|for|into|to|at|with|ref|reference|using|thru|through)\b|\s+w/|\s+\(?\+?\d[\d\s-]{5,}|[.!,;(\n]|$)"""

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
        // "You have received PHP 500.00 of GCash from JUAN DELA CRUZ w/ MSISDN 0917..."
        // "You have received PHP 1,000.00 via InstaPay from JUAN DELA CRUZ."
        Rule("""received\s+$AMOUNT\s+${MIDDLE}from\s+$SENDER_AFTER""", 2, 1),
        // "You got $350 from John Doe"
        Rule("""got\s+$AMOUNT\s+${MIDDLE}from\s+$SENDER_AFTER""", 2, 1),
        // "Payment of $350.00 from John Doe" / "Transfer of ..." / "Deposit of ..."
        Rule("""(?:payment|transfer|deposit|credit)\s+of\s+$AMOUNT\s+$MIDDLE(?:received\s+)?from\s+$SENDER_AFTER""", 2, 1),
        // "$350.00 received from John Doe" / "$350.00 from John Doe"
        Rule("""$AMOUNT\s+(?:has\s+been\s+|was\s+)?(?:received\s+|credited\s+|deposited\s+)?${MIDDLE}from\s+$SENDER_AFTER""", 2, 1),
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
        val normalized = text.replace('\u00A0', ' ').replace(Regex("""[ \t]+"""), " ")
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

    /**
     * Turns "$350.00 USD" into "$350", "PHP 1,500.50" / "P1,500.50" into
     * "1,500 pesos and 50 centavos", "350.00 CAD" into "350 CAD".
     */
    internal fun normalizeAmount(raw: String): String? {
        val s = raw.trim()
        val number = Regex(NUMBER).find(s)?.value ?: return null
        val before = s.substring(0, s.indexOf(number))
        val after = s.substring(s.indexOf(number) + number.length)

        val symbol = before.firstOrNull { it in SYMBOLS }?.toString()
        // A code before the number may be any case ("Php 500"); after it, only upper case
        // so words like "for" in "$5 for lunch" aren't mistaken for a currency.
        val code = Regex("""\b([A-Za-z]{3})\b""").find(before)?.groupValues?.get(1)?.uppercase()
            ?: Regex("""\b([A-Z]{3})\b""").find(after)?.groupValues?.get(1)
            ?: if (before.trim().equals("P", ignoreCase = true)) "PHP" else null

        // A bare number with no currency marker and no decimals is too ambiguous (could be a
        // count, a date, an order number), so only accept it if it has cents.
        if (symbol == null && code == null && !number.contains('.')) return null

        val cleanNumber = number.replace(" ", ",").removeSuffix(".00").let {
            if (Regex("""\.\d$""").containsMatchIn(it)) it + "0" else it
        }

        if (code == "PHP" || symbol == "₱") return pesos(cleanNumber)

        return when {
            symbol != null && code != null && codeToSymbol[code] != symbol ->
                "$symbol$cleanNumber $code"
            symbol != null -> "$symbol$cleanNumber"
            code != null && codeToSymbol.containsKey(code) -> "${codeToSymbol[code]}$cleanNumber"
            code != null -> "$cleanNumber $code"
            else -> cleanNumber
        }
    }

    /** Spelled out so every TTS engine says "pesos" (not all of them know the ₱ sign). */
    private fun pesos(number: String): String {
        val whole = number.substringBefore('.')
        val centavos = number.substringAfter('.', "").toIntOrNull() ?: 0
        val pesoPart = if (whole == "1") "1 peso" else "$whole pesos"
        return when (centavos) {
            0 -> pesoPart
            1 -> "$pesoPart and 1 centavo"
            else -> "$pesoPart and $centavos centavos"
        }
    }

    private val notASender = setOf("you", "someone", "a", "an", "the", "we", "i")

    internal fun cleanSender(raw: String): String? {
        val s = raw.trim()
            .removePrefix("from ").removePrefix("From ")
            .replace("*", "") // masked names like "JU** DE** C."
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '—', ',', '"', '\'')
            .trim()
        if (s.isEmpty() || s.length > 60) return null
        if (s.none { it.isLetter() }) return null
        if (s.lowercase() in notASender) return null
        return titleCaseIfShouting(s)
    }

    /** "JUAN DELA CRUZ" -> "Juan Dela Cruz", so TTS doesn't spell short words letter by letter. */
    private fun titleCaseIfShouting(name: String): String {
        if (name.any { it.isLowerCase() } || name.count { it.isLetter() } < 2) return name
        return name.split(' ').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }
    }
}
