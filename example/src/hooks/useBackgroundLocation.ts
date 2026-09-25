import { useState } from 'react';
import { PermissionsAndroid, Platform } from 'react-native';
import { GeoGuardTelemetry } from 'react-native-geoguard-telemetry';

export const useBackgroundLocation = () => {
  const [isTracking, setIsTracking] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);

  const requestAndroidPermissions = async (): Promise<boolean> => {
    if (Platform.OS !== 'android') return false;
    try {
      const fineGranted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      );
      if (fineGranted !== PermissionsAndroid.RESULTS.GRANTED) return false;

      if (Platform.Version >= 34) {
        const fgsLocationPermission =
          'android.permission.FOREGROUND_SERVICE_LOCATION';
        await PermissionsAndroid.request(fgsLocationPermission as any);
      }

      if (Platform.Version >= 29) {
        const bgGranted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.ACCESS_BACKGROUND_LOCATION
        );
        if (bgGranted !== PermissionsAndroid.RESULTS.GRANTED) return false;
      }
      return true;
    } catch (err) {
      console.error('Error requesting permissions:', err);
      return false;
    }
  };

  const startTracking = async () => {
    setLoading(true);
    try {
      const hasPermission = await requestAndroidPermissions();
      if (!hasPermission) {
        setLoading(false);
        return;
      }

      // 🚀 NESTED JSON OBJECT SPECIFICATION CONFIG
      const standardRequestConfig = {
        url: 'http://10.0.2.2:3000/api/driver/location', // Localhost for Android Emulator
        method: 'POST',
        headers: {
          Accept: 'application/json',
        },
        options: {
          interval: 5000,
          fastestInterval: 2000,
          distanceFilter: 5.0,
        },
        body: {
          driver_id: 'driver_abc_99',
          status: 'ON_TRIP',
        },
      };

      GeoGuardTelemetry.startLocationTracking(standardRequestConfig);
      setIsTracking(true);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const stopTracking = () => {
    GeoGuardTelemetry.stopLocationTracking();
    setIsTracking(false);
  };

  return { isTracking, loading, startTracking, stopTracking };
};
