import { beforeEach, describe, expect, it, jest } from '@jest/globals';

jest.mock('../NativeGeoguardTelemetry', () => ({
  __esModule: true,
  default: {
    startLocationTracking: jest.fn(),
    stopLocationTracking: jest.fn(),
  },
}));

jest.mock('react-native', () => {
  const mockAddListener = jest.fn().mockReturnValue({
    remove: jest.fn(),
  });

  return {
    Platform: {
      OS: 'android',
    },

    NativeEventEmitter: jest.fn().mockImplementation(() => ({
      addListener: mockAddListener,
    })),
  };
});

import NativeGeoguardTelemetry from '../NativeGeoguardTelemetry';
import { GeoGuardTelemetry } from '../index';

describe('GeoGuardTelemetry', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('startLocationTracking', () => {
    it('should pass configuration to the native module as JSON', () => {
      const configuration = {
        url: 'https://example.com/location',
        method: 'POST',
        headers: {
          Authorization: 'Bearer test-token',
        },
        body: {
          driverId: '123',
        },
        options: {
          interval: 5000,
          fastestInterval: 2000,
          distanceFilter: 5,
        },
      };

      GeoGuardTelemetry.startLocationTracking(configuration);

      expect(
        NativeGeoguardTelemetry.startLocationTracking
      ).toHaveBeenCalledTimes(1);

      expect(
        NativeGeoguardTelemetry.startLocationTracking
      ).toHaveBeenCalledWith(JSON.stringify(configuration));
    });
  });

  describe('stopLocationTracking', () => {
    it('should call the native stop method', () => {
      GeoGuardTelemetry.stopLocationTracking();

      expect(
        NativeGeoguardTelemetry.stopLocationTracking
      ).toHaveBeenCalledTimes(1);
    });
  });

  describe('addLocationListener', () => {
    it('should subscribe to onLocationChange', () => {
      const listener = jest.fn();

      const subscription = GeoGuardTelemetry.addLocationListener(listener);

      expect(subscription).toBeDefined();
      expect(subscription.remove).toBeDefined();
    });
  });

  describe('addNetworkResponseListener', () => {
    it('should subscribe to onNetworkResponse', () => {
      const listener = jest.fn();

      const subscription =
        GeoGuardTelemetry.addNetworkResponseListener(listener);

      expect(subscription).toBeDefined();
      expect(subscription.remove).toBeDefined();
    });
  });
});
