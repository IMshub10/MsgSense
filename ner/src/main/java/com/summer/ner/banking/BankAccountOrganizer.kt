package com.summer.ner.banking

import android.content.Context
import android.util.Base64
import com.summer.core.util.BankRegistry
import com.summer.core.data.local.dao.NerDao
import com.summer.core.data.local.entities.BankAccountBalanceObservationEntity
import com.summer.core.data.local.entities.BankAccountEntity
import com.summer.core.data.local.entities.SmsTransactionAccountLinkEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankAccountOrganizer @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: NerDao,
) {
    private val preferences = context.getSharedPreferences("bank_account_identity", Context.MODE_PRIVATE)

    suspend fun organizePending(limit: Int = 100): Int {
        val candidates = dao.accountOrganizingCandidates(limit)
        candidates.forEach { candidate ->
            val bank = BankRegistry.resolve(candidate.bank) ?: BankRegistry.resolveSender(candidate.rawAddress)
            val canonicalBank = bank?.key ?: "unknown"
            val normalizedIdentifier = candidate.account?.filter(Char::isDigit)
                ?.takeIf { it.length >= 6 && bank != null }
            val instrumentType = when {
                candidate.cardType != null -> "CARD"
                normalizedIdentifier != null -> "ACCOUNT"
                else -> "UNASSIGNED"
            }
            val identity = if (normalizedIdentifier != null) {
                "$canonicalBank|$instrumentType|$normalizedIdentifier"
            } else {
                "$canonicalBank|UNASSIGNED"
            }
            val fingerprint = fingerprint(identity)
            val existing = dao.bankAccountByFingerprint(fingerprint)
            val now = System.currentTimeMillis()
            val accountId = existing?.id ?: dao.insertBankAccount(
                BankAccountEntity(
                    canonicalBank = canonicalBank,
                    instrumentType = instrumentType,
                    generatedName = generatedName(bank?.displayName, instrumentType),
                    maskedIdentifier = normalizedIdentifier?.let(::mask),
                    identifierFingerprint = fingerprint,
                    logoKey = bank?.logoKey,
                    createdAt = now,
                    updatedAt = now,
                )
            ).takeIf { it > 0 } ?: requireNotNull(dao.bankAccountByFingerprint(fingerprint)).id

            dao.upsertAccountLink(
                SmsTransactionAccountLinkEntity(
                    extractionId = candidate.extractionId,
                    accountId = accountId,
                    source = "AUTO",
                    confidence = if (normalizedIdentifier != null) 1.0 else 0.0,
                    reason = if (normalizedIdentifier != null) "BANK_AND_IDENTIFIER" else "UNASSIGNED_BUCKET",
                    createdAt = now,
                    updatedAt = now,
                )
            )
            candidate.balance?.toDecimalOrNull()?.let { balance ->
                dao.upsertBalanceObservation(
                    BankAccountBalanceObservationEntity(
                        accountId = accountId,
                        sourceExtractionId = candidate.extractionId,
                        balance = balance,
                        currency = candidate.balanceCurrency.orEmpty(),
                        observedAt = candidate.smsDate,
                        createdAt = now,
                    )
                )
            }
        }
        return candidates.size
    }

    suspend fun organizeAll() {
        while (organizePending() > 0) Unit
    }

    suspend fun moveToUnassigned(extractionId: Long, bankValue: String?) {
        val bank = BankRegistry.resolve(bankValue)
        val canonicalBank = bank?.key ?: "unknown"
        val identityFingerprint = fingerprint("$canonicalBank|UNASSIGNED")
        val now = System.currentTimeMillis()
        val accountId = dao.bankAccountByFingerprint(identityFingerprint)?.id ?: dao.insertBankAccount(
            BankAccountEntity(
                canonicalBank = canonicalBank,
                instrumentType = "UNASSIGNED",
                generatedName = generatedName(bank?.displayName, "UNASSIGNED"),
                maskedIdentifier = null,
                identifierFingerprint = identityFingerprint,
                logoKey = bank?.logoKey,
                createdAt = now,
                updatedAt = now,
            )
        ).takeIf { it > 0 } ?: requireNotNull(dao.bankAccountByFingerprint(identityFingerprint)).id
        dao.upsertAccountLink(
            SmsTransactionAccountLinkEntity(
                extractionId = extractionId,
                accountId = accountId,
                source = "USER",
                confidence = 1.0,
                reason = "MANUAL_UNASSIGNED",
                createdAt = now,
                updatedAt = now,
            )
        )
        dao.reassignBalanceObservation(extractionId, accountId)
    }

    private fun fingerprint(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret(), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(value.toByteArray()), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun secret(): ByteArray {
        val stored = preferences.getString("hmac_secret", null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)
        val generated = ByteArray(32).also(SecureRandom()::nextBytes)
        preferences.edit().putString("hmac_secret", Base64.encodeToString(generated, Base64.NO_WRAP)).apply()
        return generated
    }

    private fun mask(value: String): String? =
        value.filter(Char::isDigit).takeIf { it.length >= 4 }?.takeLast(4)?.let { "••••$it" }

    private fun generatedName(bank: String?, type: String): String = when (type) {
        "CARD" -> "${bank ?: "Unknown bank"} card"
        "ACCOUNT" -> "${bank ?: "Unknown bank"} account"
        else -> if (bank == null) "Unassigned transactions" else "Unassigned $bank"
    }

    private fun String.toDecimalOrNull(): String? =
        runCatching { BigDecimal(this).stripTrailingZeros().toPlainString() }.getOrNull()
}
