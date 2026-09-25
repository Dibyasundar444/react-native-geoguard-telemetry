import {
  NativeEventEmitter,
  // NativeModules,
  Platform,
} from 'react-native';

import NativeGeoguardTelemetry from './NativeGeoguardTelemetry';

export interface GeoGuardTrackingOptions {
  interval?: number;
  fastestInterval?: number;
  distanceFilter?: number;
}

export interface GeoGuardRequestConfiguration {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  body?: Record<string, unknown>;
  options?: GeoGuardTrackingOptions;
}

export interface GeoGuardLocation {
  latitude: number;
  longitude: number;
  timestamp: number;
}

export interface GeoGuardNetworkResponse {
  isSuccess: boolean;
  statusCode: number;
  resultBody: string;
  apiUrl: string;
  payloadSent: string;
  errorMessage: string;
  timestamp: number;
}

const eventEmitter =
  Platform.OS === 'android'
    ? new NativeEventEmitter(NativeGeoguardTelemetry)
    : null;

export const GeoGuardTelemetry = {
  startLocationTracking(configuration: GeoGuardRequestConfiguration): void {
    if (Platform.OS !== 'android') {
      throw new Error(
        'GeoGuardTelemetry is currently supported only on Android.'
      );
    }

    const configurationJson = JSON.stringify(configuration);

    NativeGeoguardTelemetry.startLocationTracking(configurationJson);
  },

  stopLocationTracking(): void {
    if (Platform.OS !== 'android') {
      return;
    }

    NativeGeoguardTelemetry.stopLocationTracking();
  },

  addLocationListener(listener: (location: GeoGuardLocation) => void) {
    if (!eventEmitter) {
      return {
        remove: () => {},
      };
    }

    return eventEmitter.addListener(
      'onLocationChange',
      listener as (...args: readonly Object[]) => unknown
    );
  },

  addNetworkResponseListener(
    listener: (response: GeoGuardNetworkResponse) => void
  ) {
    if (!eventEmitter) {
      return {
        remove: () => {},
      };
    }

    return eventEmitter.addListener(
      'onNetworkResponse',
      listener as (...args: readonly Object[]) => unknown
    );
  },
};

export { NativeGeoguardTelemetry };

export type { TurboModule } from 'react-native';
