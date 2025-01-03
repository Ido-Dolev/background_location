package com.almoullim.background_location

import android.annotation.SuppressLint
import android.app.*
import android.location.*
import android.location.LocationListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.common.*
import android.app.PendingIntent

class LocationUpdatesService : Service() {

    private var forceLocationManager: Boolean = false
    private var volumeService: VolumeService? = null
    private var vibrationService: VibrationService? = null
    private var audioService: AudioService? = null
    private var isRinging: Boolean = false

    override fun onBind(intent: Intent?): IBinder {
        val distanceFilter = intent?.getDoubleExtra("distance_filter", 0.0)
        if (intent != null) {
            forceLocationManager = intent.getBooleanExtra("force_location_manager", false)
        }
        if (distanceFilter != null) {
            createLocationRequest(distanceFilter)
        } else {
            createLocationRequest(0.0)
        }
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            println("DOLEV intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        val id = intent.getIntExtra("id", 0)
        val stopService = intent.getBooleanExtra("stop_service", false)
        val action = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ACTION)

        if (action == "STOP_ALARM" && id != 0) {
            println("DOLEV on stop!")
            stopAlarm(id, stopService)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private val mBinder = LocalBinder()
    private var mNotificationManager: NotificationManager? = null
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationManager: LocationManager? = null
    private var mFusedLocationCallback: LocationCallback? = null
    private var mLocationManagerCallback: LocationListener? = null
    private var mLocation: Location? = null
    private var isGoogleApiAvailable: Boolean = false
    private var isStarted: Boolean = false

    companion object {
        var NOTIFICATION_TITLE = "Background service is running"
        var NOTIFICATION_MESSAGE = "Background service is running"
        var NOTIFICATION_ICON = "@mipmap/ic_launcher"

        private const val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        private val TAG = LocationUpdatesService::class.java.simpleName
        private const val CHANNEL_ID = "channel_01"
        internal const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        internal const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        internal const val EXTRA_STOP_ALARM = "$PACKAGE_NAME.stop_alarm_from_service"
        internal const val EXTRA_STOP_SERVICE_ON_ALARM = "$PACKAGE_NAME.stop_service_on_alarm"
        private const val EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification"
        var UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000
        var FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
        private const val NOTIFICATION_ID = 12345678
        private lateinit var broadcastReceiver: BroadcastReceiver

        private const val STOP_SERVICE = "stop_service"
    }


    private val notification: NotificationCompat.Builder
        @SuppressLint("UnspecifiedImmutableFlag")
        get() {

            val intent = Intent(this, getMainActivityClass(this))
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
            intent.action = "Localisation"
            //intent.setClass(this, getMainActivityClass(this))
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }


            val builder = NotificationCompat.Builder(this, "BackgroundLocation")
                    .setContentTitle(NOTIFICATION_TITLE)
                    .setOngoing(true)
                    .setSound(null)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSmallIcon(resources.getIdentifier(NOTIFICATION_ICON, "mipmap", packageName))
                    .setWhen(System.currentTimeMillis())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_MESSAGE))
                    .setContentIntent(pendingIntent)
                    .setOnlyAlertOnce(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID)
            }

            return builder
        }

    private var mServiceHandler: Handler? = null

    override fun onCreate() {
        val googleAPIAvailability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(applicationContext)

        isGoogleApiAvailable = googleAPIAvailability == ConnectionResult.SUCCESS


        if (isGoogleApiAvailable && !this.forceLocationManager) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            mFusedLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Smart cast to 'Location' is impossible, because 'locationResult.lastLocation'
                    // is a property that has open or custom getter
                    val newLastLocation = locationResult.lastLocation
                    if (newLastLocation is Location) {
                        super.onLocationResult(locationResult)
                        onNewLocation(newLastLocation)
                    }
                }
            }
        } else {
            mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager?

            mLocationManagerCallback = LocationListener { location ->
                println(location.toString())
                onNewLocation(location)
            }
        }

        getLastLocation()

        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)

        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Application Name"
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
            mChannel.setSound(null, null)
            mNotificationManager!!.createNotificationChannel(mChannel)
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "stop_service") {
                    removeLocationUpdates()
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(STOP_SERVICE)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }


        updateNotification() // to start the foreground service
    }


    fun requestLocationUpdates() {
        Utils.setRequestingLocationUpdates(this, true)
        try {
            if (isGoogleApiAvailable && !this.forceLocationManager) {
                mFusedLocationClient!!.requestLocationUpdates(mLocationRequest!!,
                    mFusedLocationCallback!!, Looper.myLooper())
            } else {
                mLocationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, mLocationManagerCallback!!)
            }
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
        }
    }

    fun updateNotification() {
        if (!isStarted) {
            isStarted = true
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startForeground(NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification.build())
            }

        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification.build())
        }
    }

    fun startAlarm(
        id: Int?,
        vibrate: Boolean?,
        sound: String?,
        volumeEnforced: Boolean?,
        volume: Double?,
        notificationTitle: String?,
        notificationBody: String?,
        stopButtonText: String?,
        stopService: Boolean?,
        icon: String?
    ): Boolean {
        if (id == null || isRinging) {
            return false
        }
        vibrationService = VibrationService(this)
        volumeService = VolumeService(this)
        audioService = AudioService(this)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (vibrate == null || sound == null || volumeEnforced == null || volume == null ||
            notificationTitle == null || notificationBody == null || stopButtonText == null) {
            return false
        }
        isRinging = true
        val notificationHandler = NotificationHandler(this)
        val appIntent =
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            id,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = notificationHandler.buildNotification(
            true,
            pendingIntent,
            id,
            notificationTitle,
            notificationBody,
            stopButtonText,
            stopService,
            icon
        )
        notificationManager.notify(id, notification)
        volumeService?.requestAudioFocus()
        volumeService?.setVolume(
            volume,
            volumeEnforced,
            true,
        )

        audioService?.playAudio(
            id,
            sound,
            true,
            null,
            listOf<Int>()
        )
        if (vibrate) {
            vibrationService?.startVibrating(longArrayOf(0, 500, 500), 1)
        }

        return true
    }

    fun stopAlarm(id: Int?, stopService: Boolean?): Boolean {
        if (id == null || stopService == null || !isRinging) {
            return false
        }
        isRinging = false
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
        volumeService?.restorePreviousVolume(true)
        volumeService?.abandonAudioFocus()

        vibrationService?.stopVibrating()

        audioService?.stopAudio(id)

        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_STOP_ALARM, id)
        intent.putExtra(EXTRA_STOP_SERVICE_ON_ALARM, stopService)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        return true
    }

    fun removeLocationUpdates() {
        stopForeground(true)
        stopSelf()
    }


    private fun getLastLocation() {
        try {
            if(isGoogleApiAvailable && !this.forceLocationManager) {
                mFusedLocationClient!!.lastLocation
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful && task.result != null) {
                                mLocation = task.result
                            }
                        }
            } else {
                mLocation = mLocationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
        } catch (unlikely: SecurityException) {
        }
    }

    private fun onNewLocation(location: Location) {
        mLocation = location
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }


    private fun createLocationRequest(distanceFilter: Double) {
        mLocationRequest = LocationRequest()
        mLocationRequest!!.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest!!.smallestDisplacement = distanceFilter.toFloat()
    }


    inner class LocalBinder : Binder() {
        internal val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }


    override fun onDestroy() {
        super.onDestroy()

        audioService?.cleanUp()
        vibrationService?.stopVibrating()
        volumeService?.restorePreviousVolume(true)
        volumeService?.abandonAudioFocus()

        isStarted = false
        unregisterReceiver(broadcastReceiver)
        try {
            if (isGoogleApiAvailable && !this.forceLocationManager) {
                mFusedLocationClient!!.removeLocationUpdates(mFusedLocationCallback!!)
            } else {
                mLocationManager!!.removeUpdates(mLocationManagerCallback!!)
            }

            Utils.setRequestingLocationUpdates(this, false)
            mNotificationManager!!.cancel(NOTIFICATION_ID)
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
        }
    }

    private fun getMainActivityClass(context: Context): Class<*>? {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val className = launchIntent?.component?.className ?: return null

        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            null
        }
    }
}
