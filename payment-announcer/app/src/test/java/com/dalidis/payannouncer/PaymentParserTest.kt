package com.dalidis.payannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private fun speech(text: String) = PaymentParser.parse(text)?.toSpeech()

    @Test fun exampleFormat() {
        assertEquals("You have received $350 from Maria Santos.", speech("Maria Santos sent you $350.00"))
    }

    @Test fun keepsCents() {
        assertEquals("You have received $12.50 from Bob.", speech("Bob sent you $12.50"))
        assertEquals("You have received $12.50 from Bob.", speech("Bob sent you $12.5"))
    }

    @Test fun thousandsSeparator() {
        assertEquals("You have received $1,250 from Acme Ltd.", speech("Acme Ltd sent you $1,250.00 USD"))
    }

    @Test fun payPalStyleWithCode() {
        assertEquals("You have received $350 from John Doe.", speech("John Doe sent you $350.00 USD"))
    }

    @Test fun otherCurrencyCodeKept() {
        assertEquals("You have received $40 CAD from Jane.", speech("Jane sent you $40.00 CAD"))
        assertEquals("You have received 75 CHF from Jane.", speech("Jane sent you 75.00 CHF"))
    }

    @Test fun codeOnlyMapsToSymbol() {
        assertEquals("You have received ₱1,500 from Juan Dela Cruz.", speech("You received PHP 1,500.00 from Juan Dela Cruz."))
        assertEquals("You have received €20 from Anna.", speech("Anna sent you 20.00 EUR"))
    }

    @Test fun wiseStyle() {
        assertEquals("You have received €200 from Lukas Meyer.", speech("You've received €200.00 from Lukas Meyer"))
    }

    @Test fun bankStyleTrailingClause() {
        assertEquals("You have received $350 from JOHN DOE.", speech("You received $350.00 from JOHN DOE on 14 Oct via InstaPay"))
        assertEquals("You have received $99.99 from Jane Smith.", speech("Payment of $99.99 received from Jane Smith. Ref 12345"))
        assertEquals("You have received £5 from Tom.", speech("£5.00 has been credited from Tom to your account"))
    }

    @Test fun senderAfterSentence() {
        assertEquals("You have received $5 from John Doe.", speech("You've got money! John Doe sent you $5.00"))
    }

    @Test fun paidYou() {
        assertEquals("You have received $30 from Sam.", speech("Sam paid you $30"))
    }

    @Test fun candidatesInOrder() {
        val p = PaymentParser.parse(listOf(null, "PayPal", "Kim sent you $8.00"))
        assertEquals("You have received $8 from Kim.", p?.toSpeech())
    }

    @Test fun nonPaymentReturnsNull() {
        assertNull(PaymentParser.parse("Your statement is ready"))
        assertNull(PaymentParser.parse("You have 3 new messages from John"))
        assertNull(PaymentParser.parse("You sent $20.00 to Mike"))
    }
}
