package com.candour.candoursdk.utils

import com.candour.candoursdk.interfaces.CandourListener

data class CandourSdkConfig(
    val candourApiEndpoint: String,
    val launchType: String,
    val verificationSessionId: String,
    val sessionSecret: String?,
    val savedDataSlots: Int,
    val allowOcrSkipAfterFailures: Int,
    val candourThemeConfig: CandourThemeConfig,
    val candourListener: CandourListener
)
