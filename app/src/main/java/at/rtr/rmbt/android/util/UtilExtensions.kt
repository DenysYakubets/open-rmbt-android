/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.rtr.rmbt.android.util

import android.content.Context
import at.rmbt.util.exception.HandledException
import at.rtr.rmbt.android.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun HandledException.getStringTitle(context: Context): String {
    return getTitle(context) ?: context.getString(R.string.dialog_title_error)
}

fun Calendar.format(pattern: String): String {
    val simpleDateFormat = SimpleDateFormat(pattern, Locale.US)
    return simpleDateFormat.format(this.time)
}