package ir.behnamapps.fascratch.inappbilling.learning

/** Opening or closing an ad is not itself a rewarded completion. */
internal object RewardedAdCompletion {
    fun earned(shown: Boolean, sdkRewarded: Boolean): Boolean = shown && sdkRewarded
}
