package com.candour.candoursdk.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.candour.candoursdk.interfaces.CandourExitStatus

class CandourE2eStubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = intent.getStringExtra("candourExitStatus")
            ?: CandourExitStatus.SUCCESS_SESSION.name
        setResult(RESULT_OK, Intent().putExtra("candourExitStatus", status))
        finish()
    }
}
