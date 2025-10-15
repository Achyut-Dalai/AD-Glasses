#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Device status check result
@interface DeviceStatusResult : NSObject
@property (nonatomic, readonly) BOOL isHealthy;
@property (nonatomic, readonly, nullable) NSData *configData;
@property (nonatomic, readonly, nullable) NSString *errorMessage;

- (instancetype)initWithHealthy:(BOOL)isHealthy
                     configData:(nullable NSData *)configData
                    errorMessage:(nullable NSString *)errorMessage;
@end

/// Device status check completion handler
typedef void(^DeviceStatusCheckCompletion)(DeviceStatusResult *result);

/// Device status check utility for HeyCyan smart glasses
/// Provides centralized device status verification functionality
@interface DeviceStatusCheck : NSObject

/// Perform basic device status check using Bluetooth
/// @param completion Completion handler with status result
+ (void)checkDeviceStatusViaBluetooth:(DeviceStatusCheckCompletion)completion;

/// Perform device status check via WiFi HTTP
/// @param deviceIP The IP address of the device
/// @param completion Completion handler with status result
+ (void)checkDeviceStatusViaWiFi:(NSString *)deviceIP
                      completion:(DeviceStatusCheckCompletion)completion;

/// Perform comprehensive device status check with retry logic
/// @param deviceIP The IP address of the device (optional for Bluetooth check)
/// @param useWiFi Whether to use WiFi (YES) or Bluetooth (NO) for the check
/// @param maxRetries Maximum number of retry attempts
/// @param retryDelay Delay between retry attempts in seconds
/// @param completion Completion handler with status result
+ (void)checkDeviceStatusWithRetry:(nullable NSString *)deviceIP
                           useWiFi:(BOOL)useWiFi
                       maxRetries:(NSInteger)maxRetries
                       retryDelay:(NSTimeInterval)retryDelay
                       completion:(DeviceStatusCheckCompletion)completion;

/// Validate device configuration data
/// @param configData The configuration data to validate
/// @return YES if the configuration appears valid, NO otherwise
+ (BOOL)validateDeviceConfig:(nullable NSData *)configData;

@end

NS_ASSUME_NONNULL_END