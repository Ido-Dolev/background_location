import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// BackgroundLocation plugin to get background
/// lcoation updates in iOS and Android
class BackgroundLocation {
  // The channel to be used for communication.
  // This channel is also refrenced inside both iOS and Abdroid classes
  static const MethodChannel _channel =
      MethodChannel('com.almoullim.background_location/methods');
  static final ValueNotifier<int> currentAlarmId = ValueNotifier<int>(0);

  /// Stop receiving location updates
  static Future<dynamic> stopLocationService() async {
    return await _channel.invokeMethod('stop_location_service');
  }

  /// Check if the location update service is running
  static Future<bool> isServiceRunning() async {
    var result = await _channel.invokeMethod('is_service_running');
    return result == true;
  }

  /// Start receiving location updated
  static Future<dynamic> startLocationService(
      {double distanceFilter = 0.0,
      bool forceAndroidLocationManager = false}) async {
    return await _channel
        .invokeMethod('start_location_service', <String, dynamic>{
      'distance_filter': distanceFilter,
      'force_location_manager': forceAndroidLocationManager
    });
  }

  static Future<dynamic> setAndroidNotification(
      {String? title, String? message, String? icon}) async {
    if (Platform.isAndroid) {
      return await _channel.invokeMethod('set_android_notification',
          <String, dynamic>{'title': title, 'message': message, 'icon': icon});
    }
  }

  static Future<dynamic> setAndroidConfiguration(int interval) async {
    if (Platform.isAndroid) {
      return await _channel.invokeMethod('set_configuration', <String, dynamic>{
        'interval': interval.toString(),
      });
    }
  }

  /// Check if the location update service is running
  static Future<bool> startAlarm(
      {required int id,
      required bool vibrate,
      required String sound,
      required bool volumeEnforced,
      required double volume,
      required String notificationTitle,
      required String notificationBody,
      required String stopButtonText,
      bool? stopService = false}) async {
    currentAlarmId.value = id;
    var result = await _channel.invokeMethod('start_alarm', <String, dynamic>{
      'id': id,
      'vibrate': vibrate,
      'sound': sound,
      'volumeEnforced': volumeEnforced,
      'volume': volume,
      'notification_title': notificationTitle,
      'notification_body': notificationBody,
      'stop_button_text': stopButtonText,
      'stop_service': stopService
    });
    return result == true;
  }

  static Future<bool> stopAlarm({int? id}) async {
    var result = await _channel.invokeMethod('stop_alarm', <String, dynamic>{
      'id': id ?? currentAlarmId,
    });
    currentAlarmId.value = 0;
    return result == true;
  }

  /// Register to stop alarm from notification
  static void registerStopAlarmFromNotification(Function(int) stopAlarm) {
    // _channel.setMethodCallHandler((MethodCall methodCall) async {
    //   if (methodCall.method == 'stop_alarm') {
    //     print("Stoppppppping");
    //   }
    // });
  }

  /// Get the current location once.
  Future<Location> getCurrentLocation() async {
    var completer = Completer<Location>();

    getLocationUpdates((location) {
      var loc = Location(
        latitude: location.latitude,
        longitude: location.longitude,
        accuracy: location.accuracy,
        altitude: location.altitude,
        bearing: location.bearing,
        speed: location.speed,
        time: location.time,
        isMock: location.isMock,
      );
      completer.complete(loc);
    }, (i) {});

    return completer.future;
  }

  /// Register a function to recive location updates as long as the location
  /// service has started
  static void getLocationUpdates(
      Function(Location) location, Function(int) stopAlarm) {
    // add a handler on the channel to recive updates from the native classes
    _channel.setMethodCallHandler((MethodCall methodCall) async {
      if (methodCall.method == 'location') {
        var locationData = Map.from(methodCall.arguments);
        // Call the user passed function
        location(
          Location(
              latitude: locationData['latitude'],
              longitude: locationData['longitude'],
              altitude: locationData['altitude'],
              accuracy: locationData['accuracy'],
              bearing: locationData['bearing'],
              speed: locationData['speed'],
              time: locationData['time'],
              isMock: locationData['is_mock']),
        );
      } else if (methodCall.method == 'stop_alarm_from_service') {
        var idData = Map.from(methodCall.arguments);
        stopAlarm(idData['id']);
        currentAlarmId.value = 0;
      }
    });
  }
}

/// about the user current location
class Location {
  double? latitude;
  double? longitude;
  double? altitude;
  double? bearing;
  double? accuracy;
  double? speed;
  double? time;
  bool? isMock;

  Location(
      {@required this.longitude,
      @required this.latitude,
      @required this.altitude,
      @required this.accuracy,
      @required this.bearing,
      @required this.speed,
      @required this.time,
      @required this.isMock});

  Map<String, dynamic> toMap() {
    var obj = {
      'latitude': latitude,
      'longitude': longitude,
      'altitude': altitude,
      'bearing': bearing,
      'accuracy': accuracy,
      'speed': speed,
      'time': time,
      'is_mock': isMock
    };
    return obj;
  }
}
