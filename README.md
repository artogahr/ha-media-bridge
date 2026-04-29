# HA Media Bridge

A tiny Android app that watches all active media sessions and POSTs the current track title (and optional artist / media ID) to a Home Assistant webhook whenever it changes.

Useful when an Android TV app doesn't surface media metadata through HA's built-in integration — SmartTube is the motivating case, but it works with any media app.

## How it works

The app registers a [`NotificationListenerService`](https://developer.android.com/reference/android/service/notification/NotificationListenerService), which (with user permission) can call `MediaSessionManager.getActiveSessions()` and attach a `MediaController.Callback` to every running media session. When `onMetadataChanged` fires it reads `METADATA_KEY_TITLE` (the non-display metadata key that apps set even when display variants are null) and POSTs it to your HA webhook.

```
App → MediaController.Callback.onMetadataChanged()
    → POST http://ha-host/api/webhook/<webhook-id>
        { "title": "Clair de lune",
          "artist": "Alexandre Tharaud",
          "app_package": "org.smarttube.stable",
          "media_id": "abc123XYZ" }   ← YouTube video ID if the app sets it
```

## Setup

### 1. Build the APK

#### With Nix (recommended on NixOS)

```bash
nix develop          # drops you into a shell with JDK 17 + Android SDK + adb
./gradlew assembleDebug
```

#### Without Nix

Make sure `ANDROID_HOME` points to a valid Android SDK (API 34 + build-tools 34.0.0) and a JDK 17 is on `PATH`, then:

```bash
./gradlew assembleDebug
```

The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Install on the device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configure the app

Open **HA Media Bridge** on the device, fill in:

| Field | Example |
|---|---|
| Home Assistant URL | `http://192.168.1.10:8123` |
| Webhook ID | `my_media_webhook` (choose any string) |
| Package filter | `org.smarttube.stable` (leave blank = report all apps) |

Tap **Save**, then **Grant Notification Access** and enable the service in the system settings that opens.

### 4. Create the HA webhook automation

In Home Assistant, create an automation that triggers on your webhook and does whatever you want with the payload:

```yaml
alias: SmartTube Music Mode
trigger:
  - trigger: webhook
    webhook_id: my_media_webhook
    allowed_methods: [POST]
    local_only: false
action:
  - variables:
      title:    "{{ trigger.json.title }}"
      media_id: "{{ trigger.json.get('media_id', '') }}"
  # If media_id is a YouTube video ID you can skip the search step:
  #   GET https://www.googleapis.com/youtube/v3/videos?part=snippet&id=<media_id>&key=<key>
  # and check snippet.categoryId == "10" for Music.
```

## Payload reference

```jsonc
{
  "title":       "Video or track title",       // always present
  "app_package": "com.example.player",         // always present
  "artist":      "Artist name",                // omitted if blank
  "media_id":    "dQw4w9WgXcQ"                 // omitted if blank; often a YouTube video ID
}
```

## Permissions

- `INTERNET` — to POST to Home Assistant
- `BIND_NOTIFICATION_LISTENER_SERVICE` — to read active media sessions (requires explicit user grant in system Settings → Apps → Special app access → Notification access)

No data leaves your local network.
