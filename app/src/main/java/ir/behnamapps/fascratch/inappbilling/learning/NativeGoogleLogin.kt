package ir.behnamapps.fascratch.inappbilling.learning

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import ir.behnamapps.fascratch.inappbilling.domain.CourseFailure

/** Google Play Services only. Never launches an OAuth browser or a repair/download dialog. */
internal object NativeGoogleLogin {
    fun supported(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    suspend fun token(context: Context, clientId: String, nonce: String): String? {
        if (!supported(context)) throw NativeGoogleUnavailable()
        require(clientId.endsWith(".apps.googleusercontent.com") && nonce.matches(Regex("[a-f0-9]{64}")))
        var current = context
        while (current is ContextWrapper && current !is Activity && current.baseContext !== current) current = current.baseContext
        val activity = current as? Activity ?: throw NativeGoogleUnavailable()
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (_: GetCredentialCancellationException) { return null }
        catch (_: GetCredentialProviderConfigurationException) { throw NativeGoogleUnavailable() }
        catch (_: GetCredentialUnsupportedException) { throw NativeGoogleUnavailable() }
        catch (_: NoCredentialException) { throw NativeGoogleUnavailable() }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            throw CourseFailure("پاسخ ورود گوگل معتبر نیست؛ دوباره تلاش کن.")
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

internal class NativeGoogleUnavailable : Exception("ورود گوگل روی این گوشی در دسترس نیست؛ با نام کاربری و رمز وارد شو.")
