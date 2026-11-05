package com.kitsugi.animelist.core.player

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalPlayerResultContractTest {
    private val contract = ExternalPlayerResultContract()

    @Test fun explicitZeroPositionIsNotTreatedAsMissing() {
        val longResult = contract.parseResult(Activity.RESULT_CANCELED, Intent().putExtra("position", 0L))
        val intResult = contract.parseResult(Activity.RESULT_CANCELED, Intent().putExtra("extra_position", 0))
        assertEquals(0L, longResult!!.positionMs)
        assertEquals(0L, intResult!!.positionMs)
    }

    @Test fun emptyResultIsIgnored() {
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, Intent()))
    }

    @Test fun completionWithoutPositionDoesNotInventZero() {
        val result = contract.parseResult(Activity.RESULT_OK, Intent("is.xyz.mpv.MPVActivity.result"))!!
        assertEquals(-1L, result.positionMs)
        assertNull(result.durationMs)
        assertFalse(result.endedByUser)
    }

    @Test fun completionCanUseReportedDuration() {
        val result = contract.parseResult(
            Activity.RESULT_OK,
            Intent().putExtra("end_by", "playback_completion").putExtra("duration", 120_000L)
        )!!
        assertEquals(120_000L, result.positionMs)
        assertFalse(result.endedByUser)
    }
}
