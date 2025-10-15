//
//  WiFiTransferManager.h
//  QCSDKDemo
//
//  Created by Ebowwa on 2025/10/14.
//

#import <Foundation/Foundation.h>
#import <NetworkExtension/NetworkExtension.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class GlassesMediaDownloader;

/**
 * Delegate protocol for WiFi transfer operations
 */
@protocol WiFiTransferManagerDelegate <NSObject>

- (void)wifiTransferManager:(id)manager didUpdateStatus:(NSString *)status;
- (void)wifiTransferManager:(id)manager didUpdatePreviewImage:(UIImage * _Nullable)previewImage;

@end

/**
 * Manages WiFi transfer functionality for HeyCyan smart glasses
 */
@interface WiFiTransferManager : NSObject

@property (nonatomic, weak) id<WiFiTransferManagerDelegate> delegate;
@property (nonatomic, strong, readonly) GlassesMediaDownloader *mediaDownloader;
@property (nonatomic, copy, readonly) NSString *glassesDeviceIP;
@property (nonatomic, copy, readonly) NSString *glassesSSID;
@property (nonatomic, copy, readonly) NSString *glassesPassword;

- (instancetype)initWithDelegate:(id<WiFiTransferManagerDelegate>)delegate;

// Main WiFi transfer methods
- (void)downloadMediaOverWiFi;
- (void)openMediaGallery;
- (void)switchToCaptureMode;
- (void)switchToTransferMode;

// Internal methods (made public for testing if needed)
- (void)waitForGlassesHotspotReadiness:(NSString *)ssid password:(NSString *)password;
- (void)configureWiFiConnection:(NSString *)ssid password:(NSString *)password deviceIP:(NSString *)deviceIP;
- (void)testConnectionToDevice:(NSString *)deviceIP;
- (void)startMediaDownloadFromDevice:(NSString *)deviceIP;

@end

NS_ASSUME_NONNULL_END