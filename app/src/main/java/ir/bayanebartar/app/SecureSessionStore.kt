package ir.bayanebartar.app

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Session token storage with a compatibility fallback.
 *
 * Normal devices:
 * - API token is encrypted with Android Keystore + AES/GCM.
 * - The old plaintext copy is removed after a verified encrypted read.
 *
 * Devices/OEMs where Keystore is temporarily unavailable:
 * - LocalAppPrefs/API_TOKEN remains the compatibility source.
 * - getToken() ALWAYS falls back to it, so login/API requests are not broken.
 *
 * LocalAppPrefs and SecureSessionPrefs remain excluded from Android backup
 * by the existing Step 5 backup rules.
 */
object SecureSessionStore {

    private const val TAG = "SecureSessionStore"
    private const val SECURE_PREFS = "SecureSessionPrefs"
    private const val LEGACY_PREFS = "LocalAppPrefs"
    private const val LEGACY_TOKEN_KEY = "API_TOKEN"

    private const val KEY_ALIAS = "bb_api_token_key_v1"
    private const val KEY_CIPHERTEXT = "api_token_ciphertext"
    private const val KEY_IV = "api_token_iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun getToken(context: Context): String {
        val appContext = context.applicationContext
        val legacyToken = legacyPrefs(appContext)
            .getString(LEGACY_TOKEN_KEY, "")
            .orEmpty()
            .trim()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return legacyToken
        }

        val securePrefs =
            appContext.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

        var encrypted = securePrefs.getString(KEY_CIPHERTEXT, null).orEmpty()
        var iv = securePrefs.getString(KEY_IV, null).orEmpty()

        /*
         * Existing app upgrade:
         * If the old plaintext token exists and no secure token exists yet,
         * create the encrypted copy best-effort.
         *
         * IMPORTANT: do not remove the legacy token until encrypted storage
         * has been verified by a successful decrypt.
         */
        if ((encrypted.isBlank() || iv.isBlank()) && legacyToken.isNotBlank()) {
            saveEncryptedCopy(appContext, legacyToken)
            encrypted = securePrefs.getString(KEY_CIPHERTEXT, null).orEmpty()
            iv = securePrefs.getString(KEY_IV, null).orEmpty()
        }

        if (encrypted.isNotBlank() && iv.isNotBlank()) {
            val decrypted = runCatching {
                decrypt(encrypted, iv).trim()
            }.getOrElse { error ->
                Log.w(
                    TAG,
                    "Encrypted session token could not be read; using compatibility token if available.",
                    error
                )
                clearEncryptedOnly(appContext)
                ""
            }

            if (decrypted.isNotBlank()) {
                /*
                 * Encrypted storage was proven readable.
                 * Remove a leftover plaintext migration copy.
                 */
                legacyPrefs(appContext)
                    .edit()
                    .remove(LEGACY_TOKEN_KEY)
                    .apply()

                return decrypted
            }
        }

        /*
         * Critical compatibility path:
         * Never return blank merely because Android Keystore failed while a
         * valid old/session token is still present.
         */
        return legacyToken
    }

    fun saveToken(context: Context, token: String): Boolean {
        val appContext = context.applicationContext
        val normalized = token.trim()

        if (normalized.isBlank()) {
            clearToken(appContext)
            return true
        }

        /*
         * Write the compatibility copy FIRST.
         * This guarantees that a Keystore/OEM problem can never break login
         * immediately after a successful server response.
         */
        val legacySaved = legacyPrefs(appContext)
            .edit()
            .putString(LEGACY_TOKEN_KEY, normalized)
            .commit()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return legacySaved
        }

        val secureSaved = saveEncryptedCopy(appContext, normalized)

        if (secureSaved) {
            /*
             * Verify that the just-written encrypted value is actually
             * readable on this device before deleting the compatibility copy.
             */
            val prefs =
                appContext.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

            val encrypted = prefs.getString(KEY_CIPHERTEXT, null).orEmpty()
            val iv = prefs.getString(KEY_IV, null).orEmpty()

            val verified = runCatching {
                encrypted.isNotBlank() &&
                    iv.isNotBlank() &&
                    decrypt(encrypted, iv).trim() == normalized
            }.getOrDefault(false)

            if (verified) {
                legacyPrefs(appContext)
                    .edit()
                    .remove(LEGACY_TOKEN_KEY)
                    .apply()

                return true
            }
        }

        /*
         * Secure storage was unavailable/invalid.
         * Keep the private SharedPreferences fallback so the user remains
         * logged in and authenticated API calls continue to work.
         */
        return legacySaved
    }

    fun clearToken(context: Context) {
        val appContext = context.applicationContext
        clearEncryptedOnly(appContext)
        legacyPrefs(appContext)
            .edit()
            .remove(LEGACY_TOKEN_KEY)
            .apply()
    }

    private fun saveEncryptedCopy(
        context: Context,
        token: String
    ): Boolean {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val encryptedBase64 =
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            val ivBase64 =
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

            context.getSharedPreferences(
                SECURE_PREFS,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(KEY_CIPHERTEXT, encryptedBase64)
                .putString(KEY_IV, ivBase64)
                .commit()
        }.getOrElse { error ->
            Log.w(
                TAG,
                "Android Keystore unavailable; compatibility session storage remains active.",
                error
            )
            false
        }
    }

    private fun legacyPrefs(context: Context) =
        context.getSharedPreferences(
            LEGACY_PREFS,
            Context.MODE_PRIVATE
        )

    private fun clearEncryptedOnly(context: Context) {
        context.getSharedPreferences(
            SECURE_PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .apply()
    }

    private fun decrypt(
        encryptedBase64: String,
        ivBase64: String
    ): String {
        val encrypted =
            Base64.decode(encryptedBase64, Base64.NO_WRAP)

        val iv =
            Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher =
            Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, iv)
        )

        return cipher
            .doFinal(encrypted)
            .toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }

        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
            ?.let { return it }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(
                        KeyProperties.BLOCK_MODE_GCM
                    )
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                    )
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }
}
