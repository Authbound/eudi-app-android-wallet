package com.candour.candoursdk.ui.main

import android.content.Intent
import androidx.activity.ComponentActivity
import com.candour.candoursdk.interfaces.CandourExitStatus
import com.candour.candoursdk.utils.CandourSdkConfig

object CandourSdkV2 {
    fun createLaunchIntent(
        activity: ComponentActivity,
        candourSdkConfig: CandourSdkConfig,
        launchType: String
    ): Intent = Intent(activity, CandourE2eStubActivity::class.java)
        .putExtra("candourExitStatus", CandourExitStatus.SUCCESS_SESSION.name)
}
