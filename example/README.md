# react-native-geoguard-telemetry

Android background location tracking and telemetry for React Native applications.

`react-native-geoguard-telemetry` provides continuous location tracking using an Android foreground service and can automatically send location data to your configured API endpoint.

## Features

* 📍 Background location tracking
* 🚀 Android foreground service
* 🔄 Configurable tracking interval
* 📡 Automatic location API requests
* 🧩 Dynamic latitude/longitude injection into request bodies
* 🔐 Custom HTTP headers
* 💾 Configuration persistence for service recovery
* 📢 Location update events
* 📡 Network response events
* ⚡ React Native TurboModule architecture
* 📱 Android support

> **Note:** iOS is not currently supported.

---

## Installation

### npm

```bash
npm install react-native-geoguard-telemetry
```

### Yarn

```bash
yarn add react-native-geoguard-telemetry
```

After installation, rebuild your Android application.

---

## Android Permissions

The library **automatically declares the required Android permissions through its library manifest**.

You do **not** need to manually add the following permissions to your application's `AndroidManifest.xml` just because you installed this library:

* `ACCESS_COARSE_LOCATION`
* `ACCESS_FINE_LOCATION`
* `ACCESS_BACKGROUND_LOCATION`
* `FOREGROUND_SERVICE`
* `FOREGROUND_SERVICE_LOCATION`

The library also declares its tracking service as an Android location foreground service:

```xml
<service
    android:name=".GeoGuardTrackingService"
    android:exported="false"
    android:foregroundServiceType="location" />
```

### Runtime permissions

Although the library declares the permissions automatically, **the consuming application is responsible for requesting runtime location permissions from the user**.

Request the appropriate location permissions before calling:

```ts
GeoGuardTelemetry.startLocationTracking(...)
```

The exact runtime permission flow should be implemented by the consuming application according to its Android version and location-tracking requirements.

---

## Basic Usage

```tsx
import { GeoGuardTelemetry } from 'react-native-geoguard-telemetry';

GeoGuardTelemetry.startLocationTracking({
  url: 'https://your-api.com/api/location',
  method: 'POST',

  headers: {
    Authorization: 'Bearer YOUR_TOKEN',
  },

  body: {
    driverId: '12345',
  },

  options: {
    interval: 5000,
    fastestInterval: 2000,
    distanceFilter: 5,
  },
});
```

To stop tracking:

```tsx
GeoGuardTelemetry.stopLocationTracking();
```

---

## Configuration

### `GeoGuardRequestConfiguration`

```ts
interface GeoGuardRequestConfiguration {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  body?: Record<string, unknown>;
  options?: GeoGuardTrackingOptions;
}
```

### Tracking options

```ts
interface GeoGuardTrackingOptions {
  interval?: number;
  fastestInterval?: number;
  distanceFilter?: number;
}
```

| Option            | Description                                                 |
| ----------------- | ----------------------------------------------------------- |
| `interval`        | Desired location update interval in milliseconds            |
| `fastestInterval` | Fastest allowed location update interval                    |
| `distanceFilter`  | Minimum distance in meters before requesting another update |

---

## Automatic Location Injection

GeoGuard can automatically inject the current coordinates into your request body.

For example:

```tsx
GeoGuardTelemetry.startLocationTracking({
  url: 'https://your-api.com/location',

  body: {
    driverId: '12345',
    location: {
      latitude: 0,
      longitude: 0,
    },
  },
});
```

The library replaces the coordinate values with the current device location.

It recognizes common coordinate keys including:

* `lat`
* `latitude`
* `lng`
* `long`
* `longitude`

The search is performed recursively, so nested objects are also supported.

If no coordinate fields are found, GeoGuard adds:

```json
{
  "latitude": 12.345678,
  "longitude": 78.901234
}
```

A timestamp is also added when one is not already present.

---

## Location Events

Listen for location updates:

```tsx
const subscription =
  GeoGuardTelemetry.addLocationListener((location) => {
    console.log('Latitude:', location.latitude);
    console.log('Longitude:', location.longitude);
    console.log('Timestamp:', location.timestamp);
  });
```

Remove the listener when it is no longer required:

```tsx
subscription.remove();
```

### Location event structure

```ts
interface GeoGuardLocation {
  latitude: number;
  longitude: number;
  timestamp: number;
}
```

---

## Network Response Events

GeoGuard also provides the result of each API request:

```tsx
const subscription =
  GeoGuardTelemetry.addNetworkResponseListener((response) => {
    console.log('Success:', response.isSuccess);
    console.log('Status:', response.statusCode);
    console.log('URL:', response.apiUrl);
    console.log('Response:', response.resultBody);
    console.log('Payload:', response.payloadSent);
    console.log('Error:', response.errorMessage);
  });
```

### Network response structure

```ts
interface GeoGuardNetworkResponse {
  isSuccess: boolean;
  statusCode: number;
  resultBody: string;
  apiUrl: string;
  payloadSent: string;
  errorMessage: string;
  timestamp: number;
}
```

---

## Complete Example

```tsx
import { useEffect } from 'react';

import {
  GeoGuardTelemetry,
  type GeoGuardLocation,
  type GeoGuardNetworkResponse,
} from 'react-native-geoguard-telemetry';

export default function LocationTracker() {
  useEffect(() => {
    const locationSubscription =
      GeoGuardTelemetry.addLocationListener(
        (location: GeoGuardLocation) => {
          console.log('📍 Location:', location);
        },
      );

    const networkSubscription =
      GeoGuardTelemetry.addNetworkResponseListener(
        (response: GeoGuardNetworkResponse) => {
          console.log('📡 Network response:', response);
        },
      );

    return () => {
      locationSubscription.remove();
      networkSubscription.remove();
    };
  }, []);

  const startTracking = () => {
    GeoGuardTelemetry.startLocationTracking({
      url: 'https://your-api.com/api/location',
      method: 'POST',

      headers: {
        Authorization: 'Bearer YOUR_TOKEN',
        'Content-Type': 'application/json',
      },

      body: {
        driverId: '12345',
        location: {
          latitude: 0,
          longitude: 0,
        },
      },

      options: {
        interval: 5000,
        fastestInterval: 2000,
        distanceFilter: 5,
      },
    });
  };

  const stopTracking = () => {
    GeoGuardTelemetry.stopLocationTracking();
  };

  return null;
}
```

Before calling `startTracking`, make sure the application has obtained the required runtime location permissions.

---

## Background Tracking

GeoGuard uses an Android foreground service for location tracking.

The service:

1. Receives the tracking configuration.
2. Starts Android location updates.
3. Receives location updates from the device.
4. Injects the current coordinates into the configured request body.
5. Sends the request to the configured API.
6. Emits the location event to React Native.
7. Emits the API response event to React Native.

The tracking configuration is persisted so the service can restore its configuration when required.

---

## API

### Start tracking

```ts
GeoGuardTelemetry.startLocationTracking(configuration);
```

Starts background location tracking.

### Stop tracking

```ts
GeoGuardTelemetry.stopLocationTracking();
```

Stops background location tracking.

### Location listener

```ts
GeoGuardTelemetry.addLocationListener(listener);
```

Receives location updates.

### Network listener

```ts
GeoGuardTelemetry.addNetworkResponseListener(listener);
```

Receives API request/response information.

---

## Runtime Permission Example

The consuming application should handle Android runtime permissions before starting tracking.

For example, with a permission library:

```tsx
// Request the appropriate Android location permissions
// before starting GeoGuard tracking.

await requestLocationPermissions();

GeoGuardTelemetry.startLocationTracking({
  url: 'https://your-api.com/api/location',
});
```

> GeoGuard declares the required permissions in its Android library manifest, but it does not automatically display the Android runtime permission dialog on behalf of the consuming application.

---

## Development

Clone the repository and install dependencies:

```bash
yarn install
```

Run type checking:

```bash
yarn typecheck
```

Run tests:

```bash
yarn test
```

Build the library:

```bash
yarn prepare
```

Run the example application:

```bash
yarn example android
```

---

## Testing

Run the package tests:

```bash
yarn jest src/__tests__/index.test.tsx
```

---

## Android Architecture

GeoGuard is implemented using a React Native TurboModule with a native Kotlin Android foreground service.

```text
React Native Application
        │
        ▼
GeoGuardTelemetry
        │
        ▼
TurboModule
        │
        ▼
GeoGuardTrackingService
        │
        ├── Android Fused Location Provider
        │
        ├── Location Updates
        │
        ├── Request Payload
        │
        └── HTTP API
```

---

## Requirements

* React Native
* Android
* Kotlin
* Android location services
* Runtime location permissions granted by the consuming application

---

## Important Notes

* This package currently supports **Android only**.
* The consuming application must request runtime location permissions.
* The library automatically declares its required Android permissions.
* A foreground service is used for background location tracking.
* The API endpoint must be reachable from the Android device.
* The consuming application is responsible for authentication and authorization configuration.
* Location tracking behavior may depend on Android version, device manufacturer, battery optimization, and system location settings.

---

## License

MIT