# librelay-android

## Description
libsignal-android

It includes these components and libraries:

### **libsignal-service-java**
Messaging via the Signal Server API and client. See https://github.com/ForstaLabs/libsignal-service-java for more details.

Librelay-android includes an implementation of SignalProtocolStore from the libsignal-service library that provides for persistent local storage of crypto key material:
- IdentityKeyStore
- PreKeyStore
- SessionStore
- SignedPreKeyStore

### **webrtc-android (org.whispersystems)**
An implementation of the WebRTC protocol for Android.

---
Librelay-android also includes the following services and components:

### Provisioning and Authentication
Atlas API, Signal Server API
The registration service provides authentication and provisioning of clients to both the Atlas and Signal Server APIs.

### Messaging
Firebase push messaging services

Local storage and message management services 
- Message storage
- Thread and thread preferences storage
- Message delivery services
  - Content and media messages
  - Key exchange messages
  - Expiring messages
  - Control messages
  - Multi-device Sync messages

### User Directory
Atlas User and tag directory storage and services
- User and tag storage
- User directory sync services
- Tag sync services

### Video Calling
The WebRtcCallService provides a full implmentation of the webrtc 'signaling' messages that are required to setup WebRTC connections between clients.

## Installation
You will need to include repositories used in the librelay library in order to build
```
repositories {
        maven {
            url "https://maven.google.com"
        }
        maven {
            url "https://raw.github.com/whispersystems/maven/master/preferencefragment/releases/"
        }
        maven {
            url "https://raw.github.com/whispersystems/maven/master/smil/releases/"
        }
        maven {
            url "https://raw.github.com/whispersystems/maven/master/shortcutbadger/releases/"
        }
        maven { // textdrawable
            url 'https://dl.bintray.com/amulyakhare/maven'
        }
        maven {
            url 'https://jitpack.io'
        }
}
```        
Add the library dependency into your application's build.gradle file.
```
dependencies {
   ...
   implementation 'com.github.ForstaLabs:librelay-android:v1.0.6'
   ...
}
```

Current beta release also requires the following changes to your application's build.gradle
```
android {
    ...
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    ...
```
And add this dependency to build.gradle
```
dependencies {
    ...
    implementation 'com.android.support:appcompat-v7:28.0.0'
    implementation 'com.android.support:design:28.0.0'
    ...
}
```

Finally, your projects ApplicationContext will need to extend from io.forsta.librelay.ApplicattionContext.java
```
public class ApplicationContext extends io.forsta.librelay.ApplicationContext {}
```

## License
Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

- Copyright 2014-2016 Open Whisper Systems
- Copyright 2017-2019 Forsta, Inc.