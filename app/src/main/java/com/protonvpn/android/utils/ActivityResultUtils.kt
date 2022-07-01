/*
 * Copyright (c) 2021. Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * Utility functions for using ActivityResultContract.
 *
 * Example:
 *
 *   class GetStuffActivity : Activity {
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *       super.onCreate(savedInstanceState)
 *       val input = getInput(intent)
 *       ...
 *     }
 *
 *     fun returnResult(parcelableOutput: ParcelableOutput) {
 *       ActivityResultUtils.setResult(this, parcelableOutput)
 *       finish()
 *     }
 *
 *     companion object {
 *       fun createContract() = ActivityResultUtils<ParcelableInput, ParcelableOutput>(GetStuffActivity::class)
 *       private fun getInput(intent: Intent): ParcelableInput? = ActivityResultUtils.getInput(intent)
 *     }
 *   }
 *
 *   class OtherActivity : Activity {
 *     val getStuff = registerForActivityResult(GetStuffActivity.createContract()) { parcelableOutput ->
 *       ...
 *     }
 *   }
 */
object ActivityResultUtils {
    const val INPUT_KEY = "input"
    const val OUTPUT_KEY = "output"

    inline fun <reified I : Parcelable, O : Parcelable> createContract(
        clazz: KClass<out Activity>
    ): ActivityResultContract<I, O?> {
        return object : ActivityResultContract<I, O?>() {
            override fun createIntent(context: Context, input: I): Intent =
                Intent(context, clazz.java).apply {
                    putExtra(INPUT_KEY, input)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): O? =
                if (resultCode == Activity.RESULT_OK) {
                    intent?.getParcelableExtra(OUTPUT_KEY)
                } else {
                    null
                }
        }
    }

    inline fun <reified I : Serializable, O : Serializable> createSerializableContract(
        clazz: KClass<out Activity>
    ): ActivityResultContract<I, O?> {
        return object : ActivityResultContract<I, O?>() {
            override fun createIntent(context: Context, input: I): Intent =
                Intent(context, clazz.java).apply {
                    putExtra(INPUT_KEY, input)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): O? =
                if (resultCode == Activity.RESULT_OK) {
                    intent?.getSerializableExtra(OUTPUT_KEY) as O?
                } else {
                    null
                }
        }
    }

    fun <O : Parcelable> setResult(activity: Activity, result: O) {
        activity.setResult(Activity.RESULT_OK, Intent().apply { putExtra(OUTPUT_KEY, result) })
    }

    fun <O : Serializable> setResult(activity: Activity, result: O) {
        activity.setResult(Activity.RESULT_OK, Intent().apply { putExtra(OUTPUT_KEY, result) })
    }

    fun <I : Parcelable> getInput(intent: Intent): I? = intent.getParcelableExtra(INPUT_KEY)
    fun <I : Serializable> getSerializableInput(intent: Intent): I? = intent.getSerializableExtra(INPUT_KEY) as I?
}
