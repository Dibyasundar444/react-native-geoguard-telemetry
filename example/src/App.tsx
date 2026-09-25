import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  GeoGuardTelemetry,
  type GeoGuardLocation,
  type GeoGuardNetworkResponse,
} from 'react-native-geoguard-telemetry';
import { useBackgroundLocation } from '../src/hooks/useBackgroundLocation';

export default function LocationTracker() {
  // Pull states and helper functions straight out of your custom hook
  const { isTracking, loading, startTracking, stopTracking } =
    useBackgroundLocation();
  const [currentCoords, setCurrentCoords] = useState<GeoGuardLocation | null>(
    null
  );

  useEffect(() => {
    // 📍 Subscribe to location events
    const locationSubscription = GeoGuardTelemetry.addLocationListener(
      (event: GeoGuardLocation) => {
        console.log('📍 Location received:', event);
        setCurrentCoords(event);
      }
    );
    // 📡 Subscribe to network/API response events
    const networkSubscription = GeoGuardTelemetry.addNetworkResponseListener(
      (event: GeoGuardNetworkResponse) => {
        console.log('====== 📡 Native API Network Log ======');
        console.log(`URL Target: ${event.apiUrl}`);
        console.log(
          `Status Sync: ${event.isSuccess ? '✅ SUCCESS' : '❌ FAILED'} (${event.statusCode})`
        );
        console.log(`Payload Out: ${event.payloadSent}`);
        console.log(`Server In: ${event.resultBody}`);
        if (event.errorMessage) {
          console.log(`Error: ${event.errorMessage}`);
        }
        console.log('=======================================');
        // Optional:
        // You can update UI state here if required.
      }
    );
    // 🧹 Remove listeners when screen unmounts
    return () => {
      locationSubscription.remove();
      networkSubscription.remove();
    };
  }, []);

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" />

      <View style={styles.header}>
        <Text style={styles.headerTitle}>Tracking Control Panel</Text>
      </View>

      <View style={styles.statusCard}>
        <Text style={styles.statusLabel}>Driver Duty Status:</Text>
        <Text
          style={[
            styles.statusValue,
            isTracking ? styles.activeText : styles.inactiveText,
          ]}
        >
          {isTracking ? '● ON DUTY (TRACKING LIVE)' : '○ OFF DUTY'}
          {currentCoords && (
            <Text style={{ fontSize: 12, color: '#AAA' }}>
              {'\n'}Current Location: Lat {currentCoords.latitude.toFixed(5)},
              Lng {currentCoords.longitude.toFixed(5)}
            </Text>
          )}
        </Text>
      </View>

      <View style={styles.buttonContainer}>
        {loading ? (
          <ActivityIndicator size="large" color="#2196F3" />
        ) : !isTracking ? (
          <TouchableOpacity
            style={[styles.button, styles.startButton]}
            onPress={() => {
              console.log('Starting tracking...');
              startTracking();
            }}
          >
            <Text style={styles.buttonText}>Go On Duty</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={[styles.button, styles.stopButton]}
            onPress={() => {
              console.log('Stopping tracking...');
              stopTracking();
            }}
          >
            <Text style={styles.buttonText}>Go Off Duty</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#121212',
  },
  header: {
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#222',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#FFF',
  },
  statusCard: {
    backgroundColor: '#1E1E1E',
    margin: 20,
    padding: 20,
    borderRadius: 12,
  },
  statusLabel: {
    fontSize: 14,
    color: '#AAA',
    marginBottom: 5,
  },
  statusValue: {
    fontSize: 20,
    fontWeight: '800',
  },
  activeText: {
    color: '#4CAF50',
  },
  inactiveText: {
    color: '#F44336',
  },
  buttonContainer: {
    paddingHorizontal: 20,
    marginTop: 'auto',
    marginBottom: 30,
  },
  button: {
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  startButton: {
    backgroundColor: '#2196F3',
  },
  stopButton: {
    backgroundColor: '#E53935',
  },
  buttonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
  },
});
