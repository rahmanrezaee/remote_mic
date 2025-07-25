# Remote Mic 📱🎙️📹

Transform two phones into a synchronized recording system - one as microphone, one as camera.

![Features Overview](images/feature.png)

## ✨ Key Features

🔗 **P2P Connection** • 🎭 **Dynamic Roles** • 🎥 **4K Recording** • 🎙️ **Pro Audio** • 📁 **Auto Transfer** • 🎬 **Audio-Video Merge**

## 📸 Screenshots

| Connection | Role Selection | Camera |
|:----------:|:--------------:|:------:|
| ![](images/photo_1.jpg) | ![](images/photo_2.jpg) | ![](images/photo_3.jpg) |

| Microphone | Recording | Merged |
|:----------:|:---------:|:------:|
| ![](images/photo_4.jpg) | ![](images/photo_5.jpg) | ![](images/photo_1.jpg) |

## 🚀 Features

### 📡 Device Connection
- Direct P2P connection via Google Nearby API
- Host & search functionality
- Auto device discovery
- Encrypted communication

### 🎭 Role Management
- Choose Camera or Microphone role
- Switch roles without reconnecting
- Adaptive UI for each role

### 🎥 Camera Features
- 4K UHD, FHD, HD, SD quality options
- Front/rear camera switching
- Flash control (Auto/On/Off)
- Recording timer with countdown (1s, 3s, 5s, 10s)

### 🎙️ Audio Recording
- High-fidelity audio capture
- Synchronized with camera recording
- Real-time status display
- Audio visualization feedback

### 📁 File Management
- Automatic audio transfer to camera device
- Smart file organization
- Preview before merging
- File size optimization

### 🎬 Merging
- Seamless audio-video combination
- Professional output quality
- Interactive merge dialogs
- Multiple export formats

## 🎯 How to Use

### 1. Setup Connection
1. **Device A**: Open app → "Host Connection"
2. **Device B**: Open app → "Search for Devices" → Select Device A
3. Connection established automatically

### 2. Choose Roles
- Select **Camera** or **Microphone** on each device
- Interface adapts to your role

### 3. Record
- **Camera device**: Adjust settings → Optional timer → Record
- **Microphone device**: Recording starts automatically
- Stop on either device stops both

### 4. Merge Files
- Audio transfers to camera device automatically
- Choose "Continue Editing" or "Discard"
- Export merged video

## 🛠️ Technical Info

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM + StateFlow
- **Connection**: Google Nearby API
- **Camera**: CameraX
- **Audio**: MediaRecorder

### Requirements
- Android 7.0+
- Camera, microphone, storage permissions
- Location permission for device discovery

## 📥 Installation

```bash
git clone https://github.com/yourusername/remote-mic.git
./gradlew assembleDebug
