package me.yardenac.comaphone

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer


class MainActivity : AppCompatActivity() {
    private var PERMISSION_REQUEST_READ_PHONE_STATE = 1

    private var mMenu : Menu? = null
    private var mTestMode = false
    private var mTestRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Util.hasRequiredSensors(applicationContext)) {
            Toast.makeText(
                applicationContext,
                getString(R.string.could_not_bind_sensor),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val android9Warning: TextView = findViewById(R.id.missing_support_android_9)
            android9Warning.visibility = View.GONE
        }

        if (Util.hasMagnetometer(applicationContext)) {
            val magnetometerWarning: TextView = findViewById(R.id.missing_support_magnetometer)
            magnetometerWarning.visibility = View.GONE
        }

        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS
            ), PERMISSION_REQUEST_READ_PHONE_STATE
        )

        val answerFeature: CheckedTextView = findViewById(R.id.feature_answer)
        val answerAllAnglesFeature: CheckedTextView = findViewById(R.id.feature_answer_all_angles)
        val declineFeature: CheckedTextView = findViewById(R.id.feature_decline)
        val beepBehaviour: CheckedTextView = findViewById(R.id.behaviour_beep)
        val vibrateBehaviour: CheckedTextView = findViewById(R.id.behaviour_vibrate)

        answerFeature.setOnClickListener { _->
            setAnswerFeature(!answerFeature.isChecked, true)
        }

        answerAllAnglesFeature.setOnClickListener {
            setAnswerAllAnglesFeatureIfSupported(!answerAllAnglesFeature.isChecked)
        }

        declineFeature.setOnClickListener { _->
            setDeclineFeatureIfSupported(!declineFeature.isChecked)
        }

        beepBehaviour.setOnClickListener {
            setBeepBehaviour(!beepBehaviour.isChecked)
        }

        vibrateBehaviour.setOnClickListener {
            setVibrateBehaviour(!vibrateBehaviour.isChecked)
        }

        setAnswerFeature(Util.answerFeatureEnabled(applicationContext), false)
        setAnswerAllAnglesFeatureIfSupported(Util.answerAllAnglesFeatureEnabled(applicationContext))
        setDeclineFeatureIfSupported(Util.declineFeatureEnabled(applicationContext))

        setBeepBehaviour(Util.beepBehaviourEnabled(applicationContext))
        setVibrateBehaviour(Util.vibrateBehaviourEnabled(applicationContext))

        // Debugging
        val debugLog: TextView = findViewById(R.id.debug_log)
        debugLog.setOnClickListener {
            val clipboard: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                "ComaPhone Debug Log", Util.getLog().value!!.joinToString(
                    separator = "\n"
                )
            )
            clipboard.setPrimaryClip(clip)

            Toast.makeText(
                this,
                getString(R.string.debug_log_copied_to_clipboard),
                Toast.LENGTH_LONG
            ).show()
        }

        val testButton: Button = findViewById(R.id.test_button)
        testButton.setOnClickListener {
            if (!mTestRunning) {
                if (!Util.startSensorListener(applicationContext, true)) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.enable_at_least_one_feature),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                Toast.makeText(
                    applicationContext,
                    getString(R.string.test_started),
                    Toast.LENGTH_SHORT
                ).show()

                Util.clearLog()
                Util.log("TEST STARTED")

                testButton.text = getString(R.string.end_test)

                mTestRunning = true
            } else {
                endTest()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        showTestMode(mTestMode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_PHONE_STATE -> {
                if (!grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.permissions_needed),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    finish()
                }
                return
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mMenu = menu

        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.privacy_policy -> {
                val browserIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/yardenac/comaphone/PRIVACY_POLICY.md")
                    )
                startActivity(browserIntent)
                true
            }
            R.id.test_mode -> {
                if (!mTestMode) {
                    enableTestMode(true)
                } else {
                    enableTestMode(false)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun endTest() {
        Util.stopSensorListener(this)

        Util.log("TEST ENDED")

        AlertDialog.Builder(this)
            .setTitle(R.string.test_ended)
            .setMessage(R.string.test_succesful_question)
            .setPositiveButton(R.string.close, null)
            .setNegativeButton(R.string.report_issue) { _, _ ->
                val emailDataBuilder = StringBuilder()
                emailDataBuilder.append("Product: " + android.os.Build.PRODUCT + "\n")
                emailDataBuilder.append("Model: " + android.os.Build.MODEL + "\n")
                emailDataBuilder.append("Device: " + android.os.Build.DEVICE + "\n")
                emailDataBuilder.append("SDK: " + android.os.Build.VERSION.SDK_INT + "\n")
                emailDataBuilder.append("App version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")" + "\n")
                emailDataBuilder.append("Debug log:" + "\n")
                emailDataBuilder.append(
                    Util.getLog().value!!.joinToString(
                        separator = "\n"
                    )
                )

                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse("mailto:")
                intent.putExtra(
                    Intent.EXTRA_EMAIL,
                    "yardenack@gmail.com"
                )
                intent.putExtra(Intent.EXTRA_SUBJECT, "Coma Phone Debug Log")
                intent.putExtra(Intent.EXTRA_TEXT, emailDataBuilder.toString())
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()

        val testButton: Button = findViewById(R.id.test_button)
        testButton.text = getString(R.string.start_test)

        mTestRunning = false
    }

    private fun enableTestMode(enable: Boolean) {
        val debugLog: TextView = findViewById(R.id.debug_log)
        val testButton: Button = findViewById(R.id.test_button)
        val menuItem: MenuItem = mMenu!!.findItem(R.id.test_mode)

        if (enable) {
            menuItem.title = getString(R.string.disable_test_mode)

            showTestMode(true)
            testButton.visibility = View.VISIBLE
            debugLog.visibility = View.VISIBLE

            Util.getLog().observe(this, Observer {
                try {
                    debugLog.text = it.reversed().joinToString(separator = "\n")
                } catch (ConcurrentModificationException: Exception) {
                    // We don't care, just skip this update then...
                }
            })

            testButton.text = getString(R.string.start_test)
        } else {
            menuItem.title = getString(R.string.enable_test_mode)

            showTestMode(false)

            Util.getLog().removeObservers(this)
        }

        mTestMode = enable
    }

    private fun showTestMode(show: Boolean) {
        val debugLog: TextView = findViewById(R.id.debug_log)
        val testButton: Button = findViewById(R.id.test_button)

        testButton.visibility = if (show) View.VISIBLE else View.GONE
        debugLog.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setAnswerFeature(value: Boolean, propagate: Boolean) {
        val answerFeature: CheckedTextView = findViewById(R.id.feature_answer)
        answerFeature.isChecked = value

        Util.setAnswerFeatureEnabled(applicationContext, value)

        if (propagate) {
            setAnswerAllAnglesFeatureIfSupported(false)
        }
    }

    private fun setAnswerAllAnglesFeatureIfSupported(value: Boolean) {
        val answerAllAnglesFeature: CheckedTextView = findViewById(R.id.feature_answer_all_angles)

        if (!Util.hasMagnetometer(applicationContext)) {
            answerAllAnglesFeature.isEnabled = false
            answerAllAnglesFeature.isChecked = true
            Util.setAnswerAllAnglesFeatureEnabled(applicationContext, true)

            return
        }

        answerAllAnglesFeature.isEnabled = true
        answerAllAnglesFeature.isChecked = value

        // Exclusive with decline
        if (value) {
            setDeclineFeatureIfSupported(false)
        }

        // Makes no sense to have this turned on if answering is turned off
        if (!Util.answerFeatureEnabled(applicationContext)) {
            answerAllAnglesFeature.isEnabled = false
        }

        Util.setAnswerAllAnglesFeatureEnabled(applicationContext, value)
    }

    private fun setDeclineFeatureIfSupported(value: Boolean) {
        val declineFeature: CheckedTextView = findViewById(R.id.feature_decline)

        if (android.os.Build.VERSION.SDK_INT < 28 || !Util.hasMagnetometer(applicationContext)) {
            declineFeature.isEnabled = false
            declineFeature.isChecked = false
            Util.setDeclineFeatureEnabled(applicationContext, false)

            return
        }

        declineFeature.isEnabled = true
        declineFeature.isChecked = value

        // Exclusive with answer all angles
        if (value) {
            setAnswerAllAnglesFeatureIfSupported(false)
        }

        Util.setDeclineFeatureEnabled(applicationContext, value)
    }

    private fun setBeepBehaviour(value: Boolean) {
        val beepBehaviour: CheckedTextView = findViewById(R.id.behaviour_beep)
        beepBehaviour.isChecked = value

        Util.setBeepBehaviourEnabled(applicationContext, value)
    }

    private fun setVibrateBehaviour(value: Boolean) {
        val vibrateBehaviour: CheckedTextView = findViewById(R.id.behaviour_vibrate)
        vibrateBehaviour.isChecked = value

        Util.setVibrateBehaviourEnabled(applicationContext, value)
    }
}
