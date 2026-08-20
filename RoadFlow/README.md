# RoadFlow

RoadFlow is an Android application that helps drivers in Bosnia and Herzegovina stay informed about mobile and stationary speed camera (radar) schedules. The app aggregates publicly available radar data, displays it in a clean list and calendar view, and provides an interactive map with real-time location tracking and proximity alerts.

## Goal

The primary goal of RoadFlow is to give drivers a reliable, easy-to-use tool for checking where and when speed cameras are active across BiH cantons. Instead of manually searching multiple sources, users get a single app that:

- Fetches and parses daily radar schedules from official web sources
- Organizes data by city and canton
- Shows active radars on an interactive map
- Warns the driver when approaching an active speed camera zone while driving

## Features

### Radar List (Home)
- Daily radar schedule displayed as a scrollable list grouped by city
- Filter by canton (all 10 cantons + Brčko District)
- Pull-to-refresh to fetch the latest data
- Offline support — cached data is shown when there is no internet connection
- Active/inactive radar status based on current time

### Map
- Interactive map powered by MapLibre GL
- Radar markers with coordinates loaded from Firebase
- Live GPS tracking with heading and speed display
- Route planning via OSRM with alternative routes
- Road snapping for accurate position on the road network
- Full-screen map mode with speed overlay

### History (Calendar)
- Calendar view to browse radar schedules by date
- Select any day to see which radars were active
- Same canton filtering as the home screen

### Background Tracking & Alerts
- Foreground location service for continuous radar monitoring while driving
- Proximity alerts when entering an active radar zone
- Configurable alert radius
- Text-to-Speech (TTS) warnings in Bosnian or English
- Vibration and sound notifications
- Persistent notification showing radar tracking status
- Auto-resume tracking after device reboot

### Settings
- Default canton and favorite city selection
- Light / Dark theme
- Sound & notification preferences (TTS language, vibration, alert radius)
- Home screen widget configuration

### Home Screen Widget (Beta)
- Glance-based widget showing radar status for two favorite cities
- Updates automatically with cached or freshly fetched data

### App Shortcuts
- Quick access to the Map and Calendar (History) screens directly from the launcher

## Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Navigation | Navigation Compose |
| Map | MapLibre GL Android SDK |
| Location | Google Play Services Location |
| Networking | OkHttp |
| HTML Parsing | Jsoup |
| Routing | OSRM (Open Source Routing Machine) |
| Widget | Glance AppWidget |
| Background Work | WorkManager, Foreground Services |
| Backend Data | Firebase (radar coordinates) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |

## Project Structure

```
RoadFlow/
├── app/
│   ├── build.gradle.kts              # App-level Gradle config & dependencies
│   ├── proguard-rules.pro            # ProGuard/R8 rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # Permissions, services, receivers
│       │   ├── java/com/amko/roadflow/
│       │   │   ├── MainActivity.kt           # Entry point, navigation setup
│       │   │   ├── RoadFlowApp.kt            # Application class, init logic
│       │   │   │
│       │   │   ├── domain/
│       │   │   │   └── model/                # Domain models
│       │   │   │       ├── Canton.kt         # BiH canton enum
│       │   │   │       ├── RadarData.kt      # Radar schedule entry
│       │   │   │       ├── RadarLocation.kt  # City/location config
│       │   │   │       ├── RadarCoordinate.kt
│       │   │   │       └── FirebaseRadarItem.kt
│       │   │   │
│       │   │   ├── data/
│       │   │   │   └── local/                # Data layer (services, repos)
│       │   │   │       ├── RadarParser.kt        # HTML parsing & caching
│       │   │   │       ├── FirebaseService.kt    # Firebase coordinate fetch
│       │   │   │       ├── CoordinateRepository.kt
│       │   │   │       ├── RoutingService.kt     # OSRM route requests
│       │   │   │       ├── RoadsSnapService.kt   # Snap GPS to road network
│       │   │   │       ├── LocationTrackingService.kt
│       │   │   │       ├── RadarTrackingService.kt   # Foreground tracking
│       │   │   │       ├── RadarAlertService.kt      # TTS, sound, vibration
│       │   │   │       ├── RadarNotificationService.kt
│       │   │   │       ├── RadarStatusWorker.kt      # Background status worker
│       │   │   │       ├── RadarBootReceiver.kt      # Auto-start on boot
│       │   │   │       ├── ActiveTrackingAnimator.kt
│       │   │   │       ├── TimeProvider.kt
│       │   │   │
│       │   │   │
│       │   │   ├── presentation/
│       │   │   │   ├── screens/              # Compose screens
│       │   │   │   │   ├── MainScreen.kt         # Home / radar list
│       │   │   │   │   ├── MapScreen.kt          # Interactive map
│       │   │   │   │   ├── HistoryScreen.kt      # Calendar view
│       │   │   │   │   ├── SettingsScreen.kt
│       │   │   │   │   ├── ThemeSettingsScreen.kt
│       │   │   │   │   ├── SoundSettingsScreen.kt
│       │   │   │   │   ├── WidgetScreenSettings.kt
│       │   │   │   │   └── SplashScreen.kt         # Onboarding
│       │   │   │   │
│       │   │   │   ├── components/           # Reusable UI components
│       │   │   │   │   ├── BottomNavbar.kt
│       │   │   │   │   ├── RadarItem.kt
│       │   │   │   │   ├── RadarInfoCard.kt
│       │   │   │   │   ├── CantonPickerDropdown.kt
│       │   │   │   │   ├── LocationSearchBar.kt
│       │   │   │   │   ├── SpeedOverlay.kt
│       │   │   │   │   ├── FilterButton.kt
│       │   │   │   │   ├── NoConnectionDialog.kt
│       │   │   │   │   └── AppDropdown.kt
│       │   │   │   │
│       │   │   │   ├── viewmodel/            # ViewModels
│       │   │   │   │   ├── MainViewModel.kt
│       │   │   │   │   ├── MapViewModel.kt
│       │   │   │   │   ├── HistoryViewModel.kt
│       │   │   │   │   ├── ThemeViewModel.kt
│       │   │   │   │   └── SoundViewModel.kt
│       │   │   │   │
│       │   │   │   └── widget/               # Home screen widget
│       │   │   │       ├── FavoriteCitiesWidget.kt
│       │   │   │       └── FavoriteCitiesWidgetReceiver.kt
│       │   │   │
│       │   │   ├── ui/
│       │   │   │   └── theme/                # App theming
│       │   │   │       ├── Theme.kt
│       │   │   │       ├── AppTheme.kt
│       │   │   │       ├── Color.kt
│       │   │   │       └── Type.kt
│       │   │   │
│       │   │   └── utils/
│       │   │       └── MapBitmapUtils.kt     # Map marker bitmap helpers
│       │   │
│       │   └── res/                          # Android resources
│       │       ├── drawable/                 # Vector icons
│       │       ├── layout/                   # Widget layouts
│       │       ├── mipmap-*/                 # App launcher icons
│       │       ├── values/                   # Strings, colors, themes
│       │       └── xml/                      # Shortcuts, widget config, backup rules
│       │
│       ├── test/                             # Unit tests
│       └── androidTest/                      # Instrumented tests
│
├── gradle/
│   ├── libs.versions.toml            # Version catalog (dependencies)
│   └── wrapper/                      # Gradle wrapper
├── build.gradle.kts                  # Root Gradle config
├── settings.gradle.kts
└── gradle.properties
```

## Architecture Overview

RoadFlow follows a layered architecture inspired by Clean Architecture principles:

```
┌─────────────────────────────────────────────┐
│              Presentation Layer             │
│  Screens · Components · ViewModels · Widget │
├─────────────────────────────────────────────┤
│                Domain Layer                 │
│         Models · Business Logic             │
├─────────────────────────────────────────────┤
│                 Data Layer                  │
│  Parser · Services · Repository · Config    │
└─────────────────────────────────────────────┘
```

- **Presentation** — Jetpack Compose UI, ViewModels expose StateFlow for reactive state
- **Domain** — Pure Kotlin data classes and enums (RadarData, Canton, etc.)
- **Data** — Network calls (OkHttp), HTML parsing (Jsoup), local file caching, Firebase, GPS services

## License

Copyright © 2026 Amel Kolasević. All rights reserved.

This software is proprietary. Unauthorized copying, modification, distribution, or use of this software, via any medium, is strictly prohibited without prior written permission from the copyright holder.
