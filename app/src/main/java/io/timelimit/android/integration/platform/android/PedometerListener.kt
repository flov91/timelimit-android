/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.integration.platform.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import io.timelimit.android.R

@SuppressLint("SpecifyJobSchedulerIdRange")
class PedometerListener: JobService(), SensorEventListener {
    // Steps are reset every time the device reboots, so at first boot we need to reset
    // the stepcount to the last recorded variable (by adding the new steps).
    // Also, at (set time) we need to reset the 'daily' step counter (and record it)
    companion object {
        // Singleton for current step count - class only ever talks to one sensor
        var nextFreejobId: Int = 0
        private var stepsAtBoot: Int = 0
        private var stepsAtDailyReset: Int = 0 // should be profile-related.
        private var lastDayOfWeekReset: Int = -1
        private var currentTotalStepCount: Int = 0
        // TODO: this is an ugly way to persist steps, and should be replaced with SQLite
        private var appContext : Context? = null

        fun getLastDayOfWeekReset(): Int {
            return lastDayOfWeekReset
        }

        fun resetDailySteps(currentDayOfWeek: Int) {
            stepsAtDailyReset = currentTotalStepCount
            lastDayOfWeekReset = currentDayOfWeek
            if (appContext != null) {
                val sharedPref = appContext!!.getSharedPreferences(
                    appContext!!.getString(R.string.tmp_preference_pedometer),
                    Context.MODE_PRIVATE
                )
                with(sharedPref.edit()) {
                    // update recorded step count
                    putInt(
                        appContext!!.getString(io.timelimit.android.R.string.tmp_preference_pedometer_lastdayofweek),
                        io.timelimit.android.integration.platform.android.PedometerListener.lastDayOfWeekReset
                    )
                    putInt(
                        appContext!!.getString(io.timelimit.android.R.string.tmp_preference_pedometer_steps_at_reset),
                        io.timelimit.android.integration.platform.android.PedometerListener.stepsAtDailyReset
                    )
                    commit()
                }
            }
        }

        fun getDailySteps(): Int {
            return currentTotalStepCount - stepsAtDailyReset
        }
    }

    // TODO: populate stepsAtDailyReset by using AlarmManager

    private var jp: JobParameters? = null
    private var sensorThread : HandlerThread? = null

    override fun onStartJob(jobParameters: JobParameters?): Boolean {
        Log.v("PedometerListener", "PedometerListener starting...")
        nextFreejobId++
        jp = jobParameters
        appContext = applicationContext

        sensorThread = HandlerThread("sensor_thread")
        sensorThread!!.start()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED)
        {
            // TODO: this needs to be request at setup! ideally if this check fails we should always
            // block.
            Log.e("PedometerListener", "Need permission first!")
            return false
        }
        if (stepsAtBoot == 0)
        {
            val sharedPref = applicationContext.getSharedPreferences(applicationContext.getString(R.string.tmp_preference_pedometer), Context.MODE_PRIVATE)
            stepsAtBoot = sharedPref.getInt(getString(R.string.tmp_preference_pedometer_stepcount), 0)
            lastDayOfWeekReset = sharedPref.getInt(getString(R.string.tmp_preference_pedometer_lastdayofweek), -1)
            stepsAtDailyReset = sharedPref.getInt(getString(R.string.tmp_preference_pedometer_steps_at_reset), 0)
            Log.d("PedometerListener", "Restored pedometer info: $stepsAtBoot $stepsAtDailyReset $lastDayOfWeekReset")
        }

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepCounterSensor?.let {
            sensorManager.registerListener(this@PedometerListener, it, SensorManager.SENSOR_DELAY_NORMAL, Handler(sensorThread!!.looper))
            Log.v("PedometerListener", "Registered with TYPE_STEP_COUNTER")
        }

        return true
    }

    private fun cleanup() {
        sensorThread?.join()
        sensorThread = null
        appContext = null
        jp = null
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this@PedometerListener)
    }

    override fun onStopJob(jobParameters: JobParameters?): Boolean {
        Log.v("PedometerListener", "PedometerListener cancelled.")
        cleanup()
        return true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //Log.v("PedometerListener", "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        sensorEvent ?: return
        sensorEvent.values.firstOrNull()?.let {
            if (currentTotalStepCount == 0) {
                // if we've just started, then stepsAtBoot is actually the steps at app start, which
                // includes (it) steps since boot. So adjust it accordingly
                stepsAtBoot -= it.toInt()
                Log.d("PedometerListener", "stepsAtBoot adjusted to $stepsAtBoot")
            }
            currentTotalStepCount = stepsAtBoot + it.toInt()
            val sharedPref = applicationContext.getSharedPreferences(getString(R.string.tmp_preference_pedometer), Context.MODE_PRIVATE)
            Log.v("PedometerListener","Step count: ${it.toInt()} since boot, ${getDailySteps()} today (started at $stepsAtDailyReset), $currentTotalStepCount total")
            with (sharedPref.edit()) {
                // update recorded step count
                putInt(getString(R.string.tmp_preference_pedometer_stepcount), currentTotalStepCount)
                commit()
            }
            jobFinished(jp, false)
            cleanup()
        }
    }
}