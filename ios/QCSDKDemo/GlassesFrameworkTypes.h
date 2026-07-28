//
//  GlassesFrameworkTypes.h
//  GlassesFramework
//
//  Created on 2025/8/15.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Device action types for HeyCyan smart glasses operations
 */
typedef NS_ENUM(NSInteger, QGDeviceActionType) {
    /// Get hardware version, firmware version, and WiFi firmware versions
    QGDeviceActionTypeGetVersion = 0,

    /// Set the current device time
    QGDeviceActionTypeSetTime,

    /// Get battery level and charging status
    QGDeviceActionTypeGetBattery,

    /// Get the number of photos, videos, and audio files on the device
    QGDeviceActionTypeGetMediaInfo,

    /// Trigger the device to take a photo
    QGDeviceActionTypeTakePhoto,

    /// Start or stop video recording
    QGDeviceActionTypeToggleVideoRecording,

    /// Start or stop audio recording
    QGDeviceActionTypeToggleAudioRecording,

    /// Take AI Image
    QGDeviceActionTypeToggleTakeAIImage,

    /// Switch to Capture Mode
    QGDeviceActionTypeSwitchToCaptureMode,

    /// Switch to Transfer Mode
    QGDeviceActionTypeSwitchToTransferMode,

    /// Download media over Wi-Fi
    QGDeviceActionTypeDownloadMedia,

    /// View downloaded media gallery
    QGDeviceActionTypeViewGallery,

    /// Reserved for future use
    QGDeviceActionTypeReserved,
};

NS_ASSUME_NONNULL_END