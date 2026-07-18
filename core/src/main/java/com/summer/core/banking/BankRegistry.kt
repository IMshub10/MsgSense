package com.summer.core.banking

import java.util.Locale
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BalanceRefreshMethod { DIAL, SMS_COMPOSER, NONE }

data class BankService(
    val key: String,
    val displayName: String,
    val aliases: Set<String>,
    val logoKey: String,
    val refreshMethod: BalanceRefreshMethod = BalanceRefreshMethod.NONE,
    val destination: String? = null,
    val smsTemplate: String? = null,
    val officialSource: String? = null,
    val verifiedOn: String? = null,
    val eligibilityNote: String? = null,
    val senderPatterns: Set<String> = emptySet(),
)

object BankRegistry {
    const val VERSION = "2026-06-09"
    const val REVIEWER = "NotifAI local registry"

    private fun verified(
        key: String,
        name: String,
        destination: String,
        source: String,
        method: BalanceRefreshMethod = BalanceRefreshMethod.DIAL,
        template: String? = null,
        aliases: Set<String> = emptySet(),
    ) = BankService(
        key, name, aliases + name, key, method, destination, template, source, VERSION,
        "Use the mobile number registered with the bank. Carrier or bank charges may apply.",
        senderPatterns = aliases,
    )

    val entries: List<BankService> = listOf(
        verified("sbi", "State Bank of India", "9223766666", "https://sbi.co.in/web/personal-banking/information-services/kyc-guidelines/sbi-quick-missed-call-banking", aliases = setOf("SBI", "State Bank")),
        verified("hdfc", "HDFC Bank", "18002703333", "https://v.hdfcbank.com/htdocs/common/account-balance/index.html", aliases = setOf("HDFC")),
        verified("icici", "ICICI Bank", "9594612612", "https://www.icicibank.com/mobile-banking/sms-keywords", aliases = setOf("ICICI")),
        verified("axis", "Axis Bank", "18004195959", "https://www.axisbank.com/bank-smart/toll-free-axis-dial/toll-free-axis-dial", aliases = setOf("AXIS")),
        verified("kotak", "Kotak Mahindra Bank", "18002740110", "https://www.kotak.com/en/help-center/bank-account/account-balance-related/how-can-check-my-account-balance-.html", aliases = setOf("KOTAK")),
        verified("pnb", "Punjab National Bank", "18001802223", "https://www.pnbindia.in/Missed-Call.html", aliases = setOf("PNB")),
        verified("indian_bank", "Indian Bank", "9677633000", "https://www.indianbank.in/departments/online-customer-complaints/", aliases = setOf("IndianBank")),
        verified("rbl", "RBL Bank", "18004190610", "https://www.rblbank.com/personal-banking/accounts/savings-accounts/seniors-first-savings-account", aliases = setOf("RBL")),
        verified("indusind", "IndusInd Bank", "18002741000", "https://www.indusind.com/in/en/personal/mobile-banking-services/missed-call-banking.html", aliases = setOf("IndusInd")),
        verified("yes_bank", "YES Bank", "09223920000", "https://www.yesbank.in/content/published/api/v1.1/assets/CONTCB630B88C70B400FA193D8A26432CAE6/native/regulatorypolicies_citizencharter_pdf1.pdf", aliases = setOf("YESBANK", "YES Bank")),
        unsupported("idfc", "IDFC FIRST Bank"),
        unsupported("airtel", "Airtel Payments Bank"), unsupported("au_bank", "AU Small Finance Bank"),
        unsupported("bandhan", "Bandhan Bank"), unsupported("bob", "Bank of Baroda"),
        unsupported("boi", "Bank of India"),
        unsupported("canara", "Canara Bank"), unsupported("central_bank", "Central Bank of India"),
        unsupported("city_union", "City Union Bank"), unsupported("csb", "CSB Bank"),
        unsupported("dcb", "DCB Bank"), unsupported("dhanlaxmi", "Dhanlaxmi Bank"),
        unsupported("federal", "Federal Bank"), unsupported("idbi", "IDBI Bank"),
        unsupported("iob", "Indian Overseas Bank"), unsupported("jio", "Jio Payments Bank"),
        unsupported("jk_bank", "Jammu & Kashmir Bank"), unsupported("karnataka", "Karnataka Bank"),
        unsupported("kvb", "Karur Vysya Bank"), unsupported("maharashtra", "Bank of Maharashtra"),
        unsupported("nainital", "Nainital Bank"), unsupported("paytm", "Paytm Payments Bank"),
        unsupported("psb", "Punjab & Sind Bank"), unsupported("scb", "Standard Chartered Bank"),
        unsupported("sib", "South Indian Bank"), unsupported("tmb", "Tamilnad Mercantile Bank"),
        unsupported("uco", "UCO Bank"), unsupported("ujjivan", "Ujjivan Small Finance Bank"),
        unsupported("union_bank", "Union Bank of India"),
    )

    private fun unsupported(key: String, name: String) =
        BankService(key, name, setOf(name), key, senderPatterns = setOf(key))

    fun resolve(value: String?): BankService? {
        val normalized = value?.normalize() ?: return null
        if (normalized.isBlank()) return null
        return entries.firstOrNull { entry ->
            entry.key.normalize() == normalized ||
                entry.aliases.any { alias -> normalized == alias.normalize() }
        }
    }

    fun byKey(key: String?): BankService? = entries.firstOrNull { it.key == key }

    fun actionableByKey(key: String?): BankService? = byKey(key)?.takeIf(::isActionable)

    fun resolveSender(sender: String?): BankService? {
        val normalized = sender?.normalize() ?: return null
        return entries.firstOrNull { entry ->
            entry.senderPatterns.any { pattern ->
                pattern.normalize().length >= 3 && normalized.contains(pattern.normalize())
            }
        }
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        entries.groupBy { it.key }.filterValues { it.size > 1 }.keys
            .forEach { errors += "Duplicate bank key: $it" }
        entries.filter { it.refreshMethod != BalanceRefreshMethod.NONE }.forEach {
            if (it.destination.isNullOrBlank()) errors += "${it.key}: missing destination"
            else if (!it.destination.all(Char::isDigit) || it.destination.length !in 8..15) {
                errors += "${it.key}: invalid destination"
            }
            if (it.officialSource?.startsWith("https://") != true) errors += "${it.key}: unsafe source"
            if (it.verifiedOn.isNullOrBlank()) errors += "${it.key}: missing verification date"
            else if (!isActionable(it)) errors += "${it.key}: verification is stale or incomplete"
            if (it.refreshMethod == BalanceRefreshMethod.SMS_COMPOSER && it.smsTemplate.isNullOrBlank()) {
                errors += "${it.key}: missing SMS template"
            }
        }
        if (entries.count(::isActionable) < 10) {
            errors += "Fewer than ten verified actionable banks"
        }
        return errors
    }

    private fun String.normalize(): String =
        lowercase(Locale.US).filter(Char::isLetterOrDigit)

    private fun isActionable(service: BankService): Boolean =
        service.refreshMethod != BalanceRefreshMethod.NONE &&
            !service.destination.isNullOrBlank() &&
            service.officialSource?.startsWith("https://") == true &&
            runCatching {
                ChronoUnit.DAYS.between(LocalDate.parse(service.verifiedOn), LocalDate.now())
            }.getOrDefault(Long.MAX_VALUE) <= MAX_REVIEW_AGE_DAYS

    private const val MAX_REVIEW_AGE_DAYS = 180L
}
