package com.example.sentinelsos

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.telephony.SmsManager
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class SosManager(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var mediaRecorder: MediaRecorder? = null
    private var audioUri: Uri? = null

    companion object {
        private const val EMERGENCY_NUMBER = "112"
        private const val TAG = "SosManager"
    }

    fun saveContact(phoneNumber: String) {
        sharedPreferences.edit().putString("emergency_contact", phoneNumber).apply()
    }

    fun getContact(): String? {
        return sharedPreferences.getString("emergency_contact", null)
    }

    fun savePoliceContact(phoneNumber: String) {
        sharedPreferences.edit().putString("police_contact", phoneNumber).apply()
    }

    fun getPoliceContact(): String? {
        return sharedPreferences.getString("police_contact", null)
    }

    @SuppressLint("MissingPermission")
    fun triggerSos(onComplete: () -> Unit) {
        val contact = getContact() ?: ""
        val policeContact = getPoliceContact() ?: ""
        
        // 1. Start Audio Recording (Immediate)
        startRecording()
        
        // 2. Fetch Location
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                val lat = location?.latitude ?: 0.0
                val long = location?.longitude ?: 0.0
                
                // 3. Send silent background SMS immediately
                if (contact.isNotBlank()) sendSms(contact, lat, long)
                if (policeContact.isNotBlank()) sendSms(policeContact, lat, long)
                sendSms(EMERGENCY_NUMBER, lat, long)
                
                // 4. Wait for 15 seconds of audio, then share
                Handler(Looper.getMainLooper()).postDelayed({
                    stopRecording()
                    // Share to the private contact in foreground for verification
                    if (contact.isNotBlank()) {
                        shareToMessages(contact, lat, long)
                    }
                    onComplete()
                }, 15000)
            }
            .addOnFailureListener {
                if (contact.isNotBlank()) sendSms(contact, 0.0, 0.0)
                if (policeContact.isNotBlank()) sendSms(policeContact, 0.0, 0.0)
                sendSms(EMERGENCY_NUMBER, 0.0, 0.0)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    stopRecording()
                    onComplete()
                }, 15000)
            }
    }

    private fun sendSms(phoneNumber: String, lat: Double, long: Double) {
        val message = "SOS SENTINEL — Location: https://maps.google.com/?q=$lat,$long"
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "Background SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
        }
    }

    private fun shareToMessages(phoneNumber: String, lat: Double, long: Double) {
        if (audioUri == null) {
            Log.e(TAG, "Audio URI is null")
            return
        }

        val message = "SOS SENTINEL — Distress detected. Location: https://maps.google.com/?q=$lat,$long"

        // For modern Android, we use ACTION_SEND with the smsto URI or extra address
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/3gpp"
            putExtra(Intent.EXTRA_STREAM, audioUri)
            putExtra("address", phoneNumber)
            putExtra("sms_body", message)
            // Extra fields for various SMS apps
            putExtra("android.intent.extra.TEXT", message)
            
            // This is the specific package for Google Messages
            setPackage("com.google.android.apps.messaging")
            
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.d(TAG, "Opening Google Messages with audio and location")
        } catch (e: Exception) {
            Log.e(TAG, "Direct package failed, trying general ACTION_SENDTO fallback", e)
            val fallbackIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    private fun startRecording() {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "SOS_Evidence_${System.currentTimeMillis()}.3gp")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/3gpp")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            
            audioUri = resolver.insert(collection, contentValues)
            val fileDescriptor = audioUri?.let { resolver.openFileDescriptor(it, "w")?.fileDescriptor }

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                if (fileDescriptor != null) {
                    setOutputFile(fileDescriptor)
                }
                prepare()
                start()
                Log.d(TAG, "Recording started: $audioUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaStore recording: ${e.message}")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            Log.d(TAG, "Recording stopped and saved to Downloads folder")
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed: ${e.message}")
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }
}
