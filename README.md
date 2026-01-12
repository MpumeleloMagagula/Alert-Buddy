# Alert Buddy

**Critical Infrastructure Alerting for Altron Digital**

Alert Buddy is a mission-critical Android application designed for Altron Digital's on-call support engineers. It ensures that infrastructure alerts are never missed by providing persistent, attention-grabbing notifications that continue until explicitly acknowledged.

---

## The Problem

On-call engineers at Altron Digital face a critical challenge: **silent notification failures can lead to missed alerts**, resulting in:

- Extended system downtime
- Delayed incident response times
- SLA breaches and customer impact
- Revenue loss from unaddressed outages
- Increased stress from unreliable alerting systems

Traditional notification systems fail because:
- Push notifications can be silently dismissed or missed
- Do Not Disturb modes block critical alerts
- Notification fatigue leads to ignored messages
- App closures and device restarts stop alert delivery
- No persistent reminder for unacknowledged alerts

---

## The Solution

Alert Buddy solves these problems by providing:

### Persistent Alerting
- **Continuous audio alerts** - Beeps every 60 seconds while unread alerts exist
- **Survives app closure** - Background service continues alerting even when the app is closed
- **Boot persistence** - Automatically restarts after device reboot
- **Impossible to ignore** - Alerts continue until explicitly acknowledged

### Severity-Based Prioritization
- **Critical** (Red) - Highest priority alerts requiring immediate attention
- **Warning** (Orange) - Important issues that need timely response
- **Info** (Blue) - Informational updates and notifications

### Channel-Based Organization
- Group alerts by system or service (e.g., "Production Servers", "Database Alerts")
- View unread counts per channel at a glance
- Mark all alerts as read within a channel

### Real-Time Push Notifications
- Firebase Cloud Messaging (FCM) integration
- Instant delivery of new alerts
- Rich notifications with severity indicators

---

## Features

| Feature | Description |
|---------|-------------|
| Push Notifications | Real-time alerts via Firebase Cloud Messaging |
| Persistent Audio Alerts | 60-second interval beeping for unread alerts |
| Background Service | Continues alerting when app is closed |
| Boot Receiver | Auto-restarts service after device reboot |
| Channel Organization | Group and filter alerts by category |
| Severity Levels | Critical, Warning, and Info classifications |
| Acknowledgment System | Mark alerts as read to stop alerting |
| Offline Support | Local database storage for reliability |
| User Authentication | Secure login for on-call personnel |

---

## Target Users

- **On-Call Engineers** - Primary responders to system alerts
- **DevOps Teams** - Infrastructure monitoring and response
- **Support Staff** - Customer-facing incident responders
- **IT Operations** - System administrators and operators

---

## Why Alert Buddy?

> "When infrastructure fails, Alert Buddy ensures you know about it. No silent failures. No missed notifications. Just reliable, persistent alerting until you acknowledge the issue."

Alert Buddy is built with one goal: **ensuring critical alerts are never missed**. It's designed specifically for the demanding requirements of on-call support, where missing a single alert can have significant business impact.

---

## Technical Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Architecture:** MVVM with Repository pattern
- **Database:** Room (SQLite) for local storage
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Background Processing:** Android Foreground Service
- **Minimum SDK:** Android 8.0 (API 26)

---

## Project Structure

```
app/src/main/java/com/altron/alertbuddy/
├── data/
│   ├── Database.kt          # Room database setup
│   ├── Models.kt             # Data models (Channel, Message, User)
│   └── Repository.kt         # Data access layer
├── service/
│   ├── AlertFirebaseMessagingService.kt  # FCM message handling
│   ├── AlertService.kt       # Background alerting service
│   └── BootReceiver.kt       # Device boot receiver
├── ui/theme/
│   ├── navigation/
│   │   └── Navigation.kt     # App navigation setup
│   ├── screens/
│   │   ├── LoginScreen.kt
│   │   ├── ChannelListScreen.kt
│   │   ├── MessageListScreen.kt
│   │   ├── MessageDetailScreen.kt
│   │   └── SettingsScreen.kt
│   ├── Color.kt              # App color definitions
│   └── Theme.kt              # Material theme setup
├── AlertBuddyApplication.kt  # Application class
├── MainActivity.kt           # Main entry point
└── NotificationUtils.kt      # Notification helpers
```

---

## Setup & Configuration

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Firebase project with FCM enabled

### 1. Add Dependencies to build.gradle (Module: app)

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'com.google.gms.google-services'  // Firebase
    id 'com.google.devtools.ksp'         // For Room
}

android {
    namespace 'com.altron.alertbuddy'
    compileSdk 35

    defaultConfig {
        applicationId "com.altron.alertbuddy"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "1.0.0"
    }

    buildFeatures {
        compose true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Compose BOM
    def composeBom = platform('androidx.compose:compose-bom:2024.10.00')
    implementation composeBom

    // Compose
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.material:material-icons-extended'
    
    // Activity & Lifecycle
    implementation 'androidx.activity:activity-compose:1.9.3'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.8.6'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6'
    
    // Navigation
    implementation 'androidx.navigation:navigation-compose:2.8.3'
    
    // Room Database
    def room_version = "2.6.1"
    implementation "androidx.room:room-runtime:$room_version"
    implementation "androidx.room:room-ktx:$room_version"
    ksp "androidx.room:room-compiler:$room_version"
    
    // Firebase
    implementation platform('com.google.firebase:firebase-bom:33.5.1')
    implementation 'com.google.firebase:firebase-messaging-ktx'
    implementation 'com.google.firebase:firebase-analytics-ktx'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1'
    
    // Core
    implementation 'androidx.core:core-ktx:1.13.1'
}
```

### 2. Add to build.gradle (Project level)

```groovy
plugins {
    id 'com.android.application' version '8.6.0' apply false
    id 'org.jetbrains.kotlin.android' version '2.0.21' apply false
    id 'org.jetbrains.kotlin.plugin.compose' version '2.0.21' apply false
    id 'com.google.gms.google-services' version '4.4.2' apply false
    id 'com.google.devtools.ksp' version '2.0.21-1.0.25' apply false
}
```

### 3. Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or use your existing one
3. Add an Android app with package name: `com.altron.alertbuddy`
4. Download `google-services.json` and place it in `app/` folder
5. Enable Cloud Messaging in Firebase Console

### 4. Add Resources

Create these resource files:

**res/values/strings.xml**
```xml
<resources>
    <string name="app_name">Alert Buddy</string>
</resources>
```

**res/values/colors.xml**
```xml
<resources>
    <color name="primary">#1976D2</color>
    <color name="critical">#E53935</color>
    <color name="warning">#FB8C00</color>
    <color name="info">#1E88E5</color>
</resources>
```

**res/values/themes.xml**
```xml
<resources>
    <style name="Theme.AlertBuddy" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

---

## How It Works

1. **Alert Reception** - FCM pushes alerts to the device
2. **Local Storage** - Alerts are saved to Room database
3. **Notification Display** - System notification shown with severity
4. **Background Service** - Starts beeping if unread alerts exist
5. **User Acknowledgment** - User marks alert as read
6. **Alert Stops** - Beeping stops when all alerts are acknowledged

---

## Sending FCM Messages

### From Firebase Console (Testing)

1. Go to Firebase Console → Cloud Messaging
2. Click "Send your first message"
3. Enter notification details
4. Target your app or topic "alerts"

### From Your Backend Server

Send HTTP POST to FCM API:

```bash
curl -X POST https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "topic": "alerts",
      "data": {
        "channel": "infinity-dal-ms",
        "channelName": "Infinity DAL MS",
        "title": "CPU Alert",
        "message": "CPU usage above 80%",
        "severity": "critical",
        "timestamp": "1704110400000",
        "metadata": "{\"server\": \"prod-db-01\", \"region\": \"Cape Town\"}"
      }
    }
  }'
```

### Expected Data Payload Structure

```json
{
  "channel": "string - channel ID",
  "channelName": "string - display name",
  "title": "string - alert title",
  "message": "string - alert message body",
  "severity": "critical | warning | info",
  "timestamp": "number - unix timestamp in milliseconds",
  "metadata": "string - optional JSON with extra data"
}
```

---

## Testing the App

1. Build and run the app on your device
2. Sign in with any email/password
3. View the demo alerts
4. Tap alerts to view details and mark as read
5. Test FCM by sending a message from Firebase Console

---

## Important Notes

1. **Notification Permissions**: On Android 13+, request POST_NOTIFICATIONS permission at runtime
2. **Battery Optimization**: Users may need to exclude the app from battery optimization for reliable background alerts
3. **Replace Icons**: Update the notification icons with your actual app icons
4. **FCM Token**: Send the FCM token to your server for device-specific targeting

---

## License

Copyright 2026 Altron Digital. All rights reserved.

---

**Built for Altron Digital's on-call heroes**
