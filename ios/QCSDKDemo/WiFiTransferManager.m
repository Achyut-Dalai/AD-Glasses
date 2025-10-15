//
//  WiFiTransferManager.m
//  QCSDKDemo
//
//  Created by Ebowwa on 2025/10/14.
//

#import "WiFiTransferManager.h"
#import "GlassesMediaDownloader.h"
#import "MediaGalleryViewController.h"
#import <QCSDK/QCSDKCmdCreator.h>
#import <SystemConfiguration/CaptiveNetwork.h>
#import <CoreLocation/CoreLocation.h>

@interface WiFiTransferManager () <CLLocationManagerDelegate>

@property (nonatomic, strong) GlassesMediaDownloader *mediaDownloader;
@property (nonatomic, copy) NSString *glassesDeviceIP;
@property (nonatomic, copy) NSString *glassesSSID;
@property (nonatomic, copy) NSString *glassesPassword;
@property (nonatomic, assign) NSInteger connectionVerificationAttempts;
@property (nonatomic, strong) CLLocationManager *locationManager;
@property (nonatomic, assign) BOOL locationPermissionRequested;
@property (nonatomic, assign) BOOL locationPermissionDenied;
@property (nonatomic, assign) BOOL pendingConnectionProbeWithoutLocation;
@property (nonatomic, assign) NSInteger configurationReapplyCount;

@end

@implementation WiFiTransferManager

- (instancetype)initWithDelegate:(id<WiFiTransferManagerDelegate>)delegate {
    self = [super init];
    if (self) {
        _delegate = delegate;
    }
    return self;
}

- (void)requestLocationPermissionIfNeeded {
    if (![CLLocationManager locationServicesEnabled]) {
        NSLog(@"⚠️ Location services are disabled. WiFi status checks may be limited.");
        self.locationPermissionDenied = YES;
        return;
    }

    CLAuthorizationStatus status;
    if (@available(iOS 14.0, *)) {
        status = [CLLocationManager authorizationStatus];
    } else {
        status = [CLLocationManager authorizationStatus];
    }

    switch (status) {
        case kCLAuthorizationStatusAuthorizedAlways:
        case kCLAuthorizationStatusAuthorizedWhenInUse:
            self.locationPermissionDenied = NO;
            self.pendingConnectionProbeWithoutLocation = NO;
            return;
        case kCLAuthorizationStatusNotDetermined: {
            if (!self.locationManager) {
                self.locationManager = [[CLLocationManager alloc] init];
                self.locationManager.delegate = self;
            }
            if (!self.locationPermissionRequested) {
                self.locationPermissionRequested = YES;
                dispatch_async(dispatch_get_main_queue(), ^{
                    [self.locationManager requestWhenInUseAuthorization];
                });
            }
            break;
        }
        case kCLAuthorizationStatusRestricted:
        case kCLAuthorizationStatusDenied:
            self.locationPermissionDenied = YES;
            NSLog(@"⚠️ Location permission denied. Unable to confirm WiFi connection state.");
            break;
    }
}

- (void)locationManagerDidChangeAuthorization:(CLLocationManager *)manager API_AVAILABLE(ios(14.0)) {
    CLAuthorizationStatus status = manager.authorizationStatus;
    [self handleLocationAuthorizationStatus:status];
}

- (void)locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
    [self handleLocationAuthorizationStatus:status];
}

- (void)handleLocationAuthorizationStatus:(CLAuthorizationStatus)status {
    switch (status) {
        case kCLAuthorizationStatusAuthorizedAlways:
        case kCLAuthorizationStatusAuthorizedWhenInUse:
            self.locationPermissionDenied = NO;
            self.pendingConnectionProbeWithoutLocation = NO;
            break;
        case kCLAuthorizationStatusRestricted:
        case kCLAuthorizationStatusDenied:
            self.locationPermissionDenied = YES;
            NSLog(@"⚠️ Location permission denied. WiFi join verification limited.");
            break;
        case kCLAuthorizationStatusNotDetermined:
        default:
            break;
    }
}

#pragma mark - Main WiFi Transfer Methods

- (void)downloadMediaOverWiFi {
    __weak typeof(self) weakSelf = self;
    [self.delegate wifiTransferManager:self didUpdateStatus:@"Preparing Wi-Fi download..."];
    [self.delegate wifiTransferManager:self didUpdatePreviewImage:nil];

    self.mediaDownloader = [[GlassesMediaDownloader alloc] initWithStatusHandler:^(NSString *status, UIImage * _Nullable previewImage) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:status];
            [weakSelf.delegate wifiTransferManager:weakSelf didUpdatePreviewImage:previewImage];
        });
    }];

    [self.mediaDownloader startDownloadWithCompletion:^(NSError * _Nullable error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (error) {
                [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:[NSString stringWithFormat:@"Download failed: %@", error.localizedDescription ?: @"Unknown error"]];
            } else {
                [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:@"Download complete."];
            }
            weakSelf.mediaDownloader = nil;
        });
    }];
}

- (void)openMediaGallery {
    MediaGalleryViewController *galleryVC = [[MediaGalleryViewController alloc] init];

    // Set the media directory path
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsDirectory = paths.firstObject;
    NSString *mediaPath = [documentsDirectory stringByAppendingPathComponent:@"GlassesMedia"];
    galleryVC.mediaDirectoryPath = mediaPath;

    // Get the current view controller from the delegate if it's a UIViewController
    if ([self.delegate respondsToSelector:@selector(navigationController)]) {
        UINavigationController *navController = [self.delegate performSelector:@selector(navigationController)];
        if (navController) {
            [navController pushViewController:galleryVC animated:YES];
        }
    }
}

- (void)switchToCaptureMode {
    // Try to switch directly to capture mode
    [QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModePhoto success:^{
        NSLog(@"Successfully switched to capture mode");
    } fail:^(NSInteger currentMode) {
        NSLog(@"Failed to switch to capture mode, current mode: %zd", currentMode);
        // If switching to photo mode fails, try switching to video mode first (often works as a reset)
        [QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModeVideo success:^{
            NSLog(@"Successfully switched to video mode, now trying capture mode");
            [QCSDKCmdCreator setDeviceMode:QCOperatorDeviceModePhoto success:^{
                NSLog(@"Successfully switched to capture mode");
            } fail:^(NSInteger finalMode) {
                NSLog(@"Still failed to switch to capture mode, current mode: %zd", finalMode);
            }];
        } fail:^(NSInteger videoMode) {
            NSLog(@"Failed to switch to video mode, current mode: %zd", videoMode);
        }];
    }];
}

- (void)switchToTransferMode {
    __weak typeof(self) weakSelf = self;
    [self.delegate wifiTransferManager:self didUpdateStatus:@"Preparing glasses for WiFi transfer..."];

    [self requestLocationPermissionIfNeeded];
    self.pendingConnectionProbeWithoutLocation = NO;
    self.configurationReapplyCount = 0;

    // MARK: - HEYCYAN WiFi TRANSFER SEQUENCE
    // This is the documented HeyCyan Bluetooth-first WiFi transfer sequence
    // DO NOT MODIFY without updating documentation and testing all steps
    //
    // SEQUENCE STEPS:
    // 1. Bluetooth Connection: Connect to glasses device
    // 2. Check device status (optional heartbeat)
    // 3. Request WiFi Transfer: openWifiWithMode:QCOperatorDeviceModeTransfer
    // 4. Receive Credentials: Get SSID and password from glasses
    // 5. Check device status (optional heartbeat)
    // 6. Wait for Hotspot Ready: getDeviceWifiIPSuccess with retry logic
    // 7. Get Device IP: Receive IP address when hotspot is broadcasting
    // 8. Check device status
    // 9. Configure iOS WiFi: Apply NEHotspotConfiguration with received credentials
    // 10. Check device status: getDeviceConfigWithFinished (CRITICAL VERIFICATION STEP)
    // 11. Wait for Connection: 5-second delay for iOS to establish connection
    // 12. Test Connection: HTTP request to device IP
    // 13. Start Transfer: Begin media download
    //
    // KEY INSIGHTS:
    // - Bluetooth synchronization is CRITICAL - never skip getDeviceWifiIPSuccess
    // - Device status checks catch state inconsistencies before they cause failures
    // - iOS needs time to join WiFi after NEHotspotConfiguration (5+ seconds)
    // - Always test actual connectivity, not just WiFi association status
    // - If any step fails, show manual instructions as fallback

    // STEP 3: Check if device is ready for WiFi transfer
    if (![QCSDKCmdCreator isPeripheralFreeNow]) {
        NSLog(@"⚠️ Device is busy, waiting...");
        [self.delegate wifiTransferManager:self didUpdateStatus:@"Device busy, waiting..."];

        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [self switchToTransferMode];
        });
        return;
    }

    // STEP 4: Request WiFi Transfer - Enable WiFi transfer mode via Bluetooth
    [QCSDKCmdCreator openWifiWithMode:QCOperatorDeviceModeTransfer success:^(NSString *ssid, NSString *password) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:[NSString stringWithFormat:@"Glasses hotspot ready: %@", ssid ?: @"<unknown>"]];

        NSLog(@"🔥 SUCCESS: Glasses enabled WiFi transfer mode");
        NSLog(@"📶 Hotspot SSID: %@", ssid ?: @"(none)");
        NSLog(@"🔐 Password: %@", password ?: @"(none)");

        strongSelf.glassesSSID = ssid;
        strongSelf.glassesPassword = password;

        // STEP 4: Receive Credentials - Got SSID and password successfully
        // STEP 6: Wait for Hotspot Ready - CRITICAL Bluetooth synchronization step
        // This step ensures the glasses hotspot is actually broadcasting before attempting WiFi
        NSLog(@"⏳ Waiting for glasses hotspot to be ready...");
        [strongSelf waitForGlassesHotspotReadiness:ssid password:password];

    } fail:^(NSInteger mode) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Failed to enable WiFi transfer mode"];
        NSLog(@"🔥 FAILED: Could not enable WiFi transfer mode, current mode: %zd", mode);
    }];
}

#pragma mark - Internal Helper Methods

- (void)waitForGlassesHotspotReadiness:(NSString *)ssid password:(NSString *)password {
    NSLog(@"⏳ Waiting for glasses hotspot to be ready...");
    [self.delegate wifiTransferManager:self didUpdateStatus:@"Waiting for glasses hotspot to activate..."];

    // Step 2: Use Bluetooth to check if device is ready for WiFi connection
    // This is the key insight: wait for Bluetooth confirmation before attempting WiFi
    [self checkGlassesHotspotReadinessWithRetry:0 ssid:ssid password:password];
}

- (void)checkGlassesHotspotReadinessWithRetry:(NSInteger)retry ssid:(NSString *)ssid password:(NSString *)password {
    const NSInteger maxRetries = 10;
    __weak typeof(self) weakSelf = self;

    NSLog(@"🔍 Checking glasses hotspot readiness (attempt %ld/%ld)...", (long)(retry + 1), (long)(maxRetries));
    [self.delegate wifiTransferManager:self didUpdateStatus:[NSString stringWithFormat:@"Checking hotspot readiness (%ld/%ld)...", (long)(retry + 1), (long)(maxRetries)]];

    // STEP 7: Get Device IP - The MOST CRITICAL synchronization point
    // getDeviceWifiIPSuccess confirms the glasses hotspot is actually broadcasting
    // This is the key insight that separates working from broken implementations
    [QCSDKCmdCreator getDeviceWifiIPSuccess:^(NSString *ipAddress) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        if (ipAddress.length > 0) {
            // STEP 7 SUCCESS: Got actual device IP - hotspot is confirmed broadcasting
            NSLog(@"🎉 SUCCESS: Glasses hotspot is ready and broadcasting at %@", ipAddress);
            [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Hotspot confirmed! Configuring WiFi..."];

            // STEP 8: Verify hotspot readiness via device config BEFORE WiFi configuration
            NSLog(@"🔍 Verifying device is in WiFi hotspot mode before iOS configuration...");
            [QCSDKCmdCreator getDeviceConfigWithFinished:^(BOOL success, NSError * _Nullable configError, id _Nullable configData) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (success) {
                        NSLog(@"✅ Device config check passed - confirmed in WiFi mode");
                        NSLog(@"📊 Config data: %@", configData ?: @"(no data)");

                        // Additional device readiness check before WiFi configuration
                        if (![QCSDKCmdCreator isPeripheralFreeNow]) {
                            NSLog(@"⚠️ Device became busy, waiting before WiFi configuration...");
                            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                                [strongSelf configureWiFiConnection:ssid password:password deviceIP:ipAddress];
                            });
                            return;
                        }

                        // CRITICAL: Wait for hotspot to actually start broadcasting before iOS configuration
                        NSLog(@"⏳ Waiting 5 seconds for hotspot to actually start broadcasting...");
        
                        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(5.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                            [strongSelf confirmHotspotIsBroadcastingOverBluetooth:ssid
                                                                           password:password
                                                                           deviceIP:ipAddress
                                                                              retry:0];
                        });

                    } else {
                        NSLog(@"❌ Device config check failed: %@", configError.localizedDescription);
                        NSLog(@"❌ Device may not be fully in WiFi hotspot mode yet");

                        // Wait a bit and retry device config check
                        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                            NSLog(@"🔄 Retrying device config verification...");
                            [strongSelf checkGlassesHotspotReadinessWithRetry:retry ssid:ssid password:password];
                        });
                    }
                });
            }];

        } else {
            // Continue checking if we have retries left
            if (retry < maxRetries - 1) {
                NSLog(@"🔄 Retrying hotspot readiness check...");
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                    [strongSelf checkGlassesHotspotReadinessWithRetry:retry + 1 ssid:ssid password:password];
                });
            } else {
                NSLog(@"❌ Glasses hotspot not ready after maximum retries");
                [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Hotspot activation failed. Please try again."];
            }
        }

    } failed:^{
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        NSLog(@"❌ Failed to get WiFi IP from glasses");

        // Continue checking if we have retries left
        if (retry < maxRetries - 1) {
            NSLog(@"🔄 Retrying IP request...");
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [strongSelf checkGlassesHotspotReadinessWithRetry:retry + 1 ssid:ssid password:password];
            });
        } else {
            NSLog(@"❌ Could not get glasses WiFi IP after maximum retries");
            [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Failed to detect glasses hotspot"];
        }
    }];
}

- (void)configureWiFiConnection:(NSString *)ssid password:(NSString *)password deviceIP:(NSString *)deviceIP {
    NSLog(@"📱 Configuring WiFi connection to hotspot: %@", ssid);
    NSLog(@"🔑 Password length: %lu", (unsigned long)password.length);

    if (@available(iOS 11.0, *)) {
        NSLog(@"✅ iOS 11+ detected, proceeding with NEHotspotConfiguration");
        NEHotspotConfiguration *configuration = nil;

        if (password.length > 0) {
            configuration = [[NEHotspotConfiguration alloc] initWithSSID:ssid
                                                               passphrase:password
                                                                   isWEP:NO];
        } else {
            configuration = [[NEHotspotConfiguration alloc] initWithSSID:ssid];
        }

        // Persist configuration during retry window so iOS can continue attempting association
        configuration.joinOnce = NO;

        if (@available(iOS 13.0, *)) {
            configuration.lifeTimeInDays = @1;
        }

        [self.delegate wifiTransferManager:self didUpdateStatus:[NSString stringWithFormat:@"Joining %@ via iOS...", ssid]];

        __weak typeof(self) weakSelf = self;

        self.configurationReapplyCount = 0;

        // Clear any existing configurations for this SSID first
        NSLog(@"🧹 Clearing any existing configurations for SSID: %@", ssid);
        [[NEHotspotConfigurationManager sharedManager] removeConfigurationForSSID:ssid];

        NSLog(@"🔧 Applying NEHotspotConfiguration...");
        NSLog(@"📶 SSID: %@", ssid);
        NSLog(@"🔑 Password: %@", password.length > 0 ? @"[REDACTED]" : @"[NONE]");
        NSLog(@"🔧 joinOnce: NO (persist during transfer)");

        [[NEHotspotConfigurationManager sharedManager] applyConfiguration:configuration
                                                     completionHandler:^(NSError * _Nullable error) {
            __strong typeof(weakSelf) strongSelf = weakSelf;
            if (!strongSelf) { return; }

            dispatch_async(dispatch_get_main_queue(), ^{
                if (!error) {
                    // STEP 9 SUCCESS: NEHotspotConfiguration applied successfully
                    // NOTE: This only means iOS accepted the configuration, NOT that connection is established
                    NSLog(@"✅ WiFi configuration applied successfully");
                    [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"WiFi configured! Verifying device status..."];

                    // Store connection parameters for later steps
                    strongSelf.glassesDeviceIP = deviceIP;
                    strongSelf.glassesSSID = ssid;
                    strongSelf.glassesPassword = password;

                    // STEP 10: Check device status - CRITICAL VERIFICATION STEP
                    // This ensures the glasses device is still in the expected state after WiFi configuration
                    // Catches any device state inconsistencies that could cause connection failures
                    NSLog(@"🔍 Checking device status after WiFi configuration...");

                    [QCSDKCmdCreator getDeviceConfigWithFinished:^(BOOL success, NSError * _Nullable configError, id _Nullable configData) {
                        dispatch_async(dispatch_get_main_queue(), ^{
                            if (success) {
                                // STEP 10 SUCCESS: Device status check passed - glasses are in expected state
                                NSLog(@"✅ Device status check passed - device is ready");
                                NSLog(@"📊 Config data: %@", configData ?: @"(no data)");
                                [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Device verified! Testing connection..."];

                                // STEP 11: Wait for Connection - Give iOS time to establish WiFi connection
                                // In WiFi-dense environments, iOS needs time to find the right network among many
                                // Extended delay to handle areas with many competing WiFi networks
                                strongSelf.connectionVerificationAttempts = 0;
                                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(15.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                                    // STEP 11.5: Verify iOS actually connected to the WiFi network before testing
                                    NSLog(@"🔍 Verifying iOS actually joined the WiFi network after 15-second delay (WiFi-dense area)...");
                                    [strongSelf checkIfiOSConnectedToWiFi:ssid deviceIP:deviceIP];
                                });

                            } else {
                                NSLog(@"❌ Device status check failed: %@", configError.localizedDescription);
                                [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:[NSString stringWithFormat:@"Device error: %@", configError.localizedDescription]];
                            }
                        });
                    }];

                } else if (error.code == NEHotspotConfigurationErrorAlreadyAssociated) {
                    NSLog(@"✅ Already associated with hotspot");
                    [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Already connected! Testing..."];

                    // Store the target IP and test immediately
                    strongSelf.glassesDeviceIP = deviceIP;
                    strongSelf.glassesSSID = ssid;
                    strongSelf.glassesPassword = password;

                    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                        [strongSelf testConnectionToDevice:deviceIP];
                    });

                } else {
                    NSLog(@"❌ WiFi configuration failed: %@", error.localizedDescription);
                    NSLog(@"❌ Error code: %ld", (long)error.code);
                    NSLog(@"❌ Error domain: %@", error.domain);

                    // Provide specific debugging based on error codes
                    if (error.code == NEHotspotConfigurationErrorInvalid) {
                        NSLog(@"❌ Cause: Invalid hotspot configuration (check SSID/password format)");
                    } else if (error.code == NEHotspotConfigurationErrorAlreadyAssociated) {
                        NSLog(@"✅ Already associated with hotspot (this should have been handled earlier)");
                    } else {
                        NSLog(@"❌ Cause: Unhandled error code %ld", (long)error.code);
                        if (error.code == 7) {
                            NSLog(@"❌ Additional: User denied the hotspot configuration permission");
                        } else if (error.code == 13) {
                            NSLog(@"❌ Additional: User cancelled the hotspot configuration prompt");
                        } else if (error.code == 0) {
                            NSLog(@"❌ Additional: iOS reported no error but still failed - common iOS bug");
                        } else if (error.code == 1) {
                            NSLog(@"❌ Additional: Invalid SSID format");
                        } else if (error.code == 2) {
                            NSLog(@"❌ Additional: Invalid password format");
                        } else if (error.code == 3) {
                            NSLog(@"❌ Additional: Hotspot configuration failed (network may not exist)");
                        }
                    }

                    // Try alternative approach without hidden network flag
                    NSLog(@"🔄 Trying alternative configuration without hidden flag...");
                    if (@available(iOS 11.0, *)) {
                        NEHotspotConfiguration *fallbackConfig = nil;
                        if (password.length > 0) {
                            fallbackConfig = [[NEHotspotConfiguration alloc] initWithSSID:ssid
                                                                               passphrase:password
                                                                                   isWEP:NO];
                        } else {
                            fallbackConfig = [[NEHotspotConfiguration alloc] initWithSSID:ssid];
                        }

                        fallbackConfig.joinOnce = YES; // Single-use configuration
                        // Don't set hidden = YES for fallback attempt

                        [[NEHotspotConfigurationManager sharedManager] applyConfiguration:fallbackConfig
                                                                             completionHandler:^(NSError * _Nullable fallbackError) {
                            dispatch_async(dispatch_get_main_queue(), ^{
                                if (!fallbackError) {
                                    NSLog(@"✅ Fallback configuration succeeded");
                                    [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"WiFi configured via fallback! Verifying..."];

                                    // Continue with normal flow
                                    strongSelf.glassesDeviceIP = deviceIP;
                                    strongSelf.glassesSSID = ssid;
                                    strongSelf.glassesPassword = password;

                                    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(5.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                                        [strongSelf testConnectionToDevice:deviceIP];
                                    });
                                } else {
                                    NSLog(@"❌ Fallback configuration also failed: %@", fallbackError.localizedDescription);

                                    // Last resort: try joinOnce = YES without hidden flag
                                    NSLog(@"🔄 Last resort: trying joinOnce=YES without hidden flag...");
                                    if (@available(iOS 11.0, *)) {
                                        NEHotspotConfiguration *lastResortConfig = nil;
                                        if (password.length > 0) {
                                            lastResortConfig = [[NEHotspotConfiguration alloc] initWithSSID:ssid
                                                                                  passphrase:password
                                                                                      isWEP:NO];
                                        } else {
                                            lastResortConfig = [[NEHotspotConfiguration alloc] initWithSSID:ssid];
                                        }

                                        lastResortConfig.joinOnce = YES; // Last resort: single-use
                                        // No hidden flag, no other options

                                        [[NEHotspotConfigurationManager sharedManager] applyConfiguration:lastResortConfig
                                                                             completionHandler:^(NSError * _Nullable lastResortError) {
                                            dispatch_async(dispatch_get_main_queue(), ^{
                                                if (!lastResortError) {
                                                    NSLog(@"✅ Last resort configuration succeeded");
                                                    [strongSelf.delegate wifiTransferManager:strongSelf
                                                                                  didUpdateStatus:@"WiFi configured! Testing connection..."];

                                                    strongSelf.glassesDeviceIP = deviceIP;
                                                    strongSelf.glassesSSID = ssid;
                                                    strongSelf.glassesPassword = password;

                                                    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(5.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                                                        [strongSelf testConnectionToDevice:deviceIP];
                                                    });
                                                } else {
                                                    NSLog(@"❌ All configuration attempts failed");
                                                    [strongSelf.delegate wifiTransferManager:strongSelf
                                                                                  didUpdateStatus:[NSString stringWithFormat:@"WiFi configuration failed. Please join '%@' manually in Settings.", ssid]];
                                                }
                                            });
                                        }];
                                    }
                                }
                            });
                        }];
                    } else {
                        [strongSelf.delegate wifiTransferManager:strongSelf
                                                              didUpdateStatus:[NSString stringWithFormat:@"Auto WiFi failed: %@", error.localizedDescription]];
                    }
                }
            });
        }];
    }
}

- (void)checkIfiOSConnectedToWiFi:(NSString *)expectedSSID deviceIP:(NSString *)deviceIP {
    self.connectionVerificationAttempts += 1;
    NSInteger attempt = self.connectionVerificationAttempts;
    const NSInteger maxAttempts = 6;
    const NSTimeInterval retryDelaySeconds = 5.0;

    __weak typeof(self) weakSelf = self;

    void (^scheduleRetry)(NSString *) = ^(NSString *logMessage) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        if (logMessage.length > 0) {
            NSLog(@"%@", logMessage);
        }

        if (strongSelf.locationPermissionDenied) {
            [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Location permission is required to verify WiFi connection automatically. Please enable location access for QCSDKDemo in Settings."];
            if (!strongSelf.pendingConnectionProbeWithoutLocation && deviceIP.length > 0 && attempt >= maxAttempts) {
                strongSelf.pendingConnectionProbeWithoutLocation = YES;
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                    strongSelf.pendingConnectionProbeWithoutLocation = NO;
                    [strongSelf testConnectionToDevice:deviceIP];
                });
            }
        }

        if (attempt < maxAttempts) {
            [strongSelf reapplyHotspotConfigurationIfEligibleForAttempt:attempt
                                                             expectedSSID:expectedSSID
                                                                 password:strongSelf.glassesPassword
                                                             maxAttempts:maxAttempts];

            NSString *status = [NSString stringWithFormat:@"Waiting for iOS to join %@ (%ld/%ld)...",
                                 expectedSSID ?: @"hotspot",
                                 (long)attempt,
                                 (long)maxAttempts];
            [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:status];

            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(retryDelaySeconds * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [strongSelf checkIfiOSConnectedToWiFi:expectedSSID deviceIP:deviceIP];
            });
        } else {
            NSLog(@"❌ iOS did NOT connect to WiFi: %@ after %ld attempts", expectedSSID, (long)attempt);
            [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:[NSString stringWithFormat:@"iOS didn't join %@ automatically. Please open Settings ▸ Wi-Fi and connect manually, then return to the app.", expectedSSID ?: @"the hotspot"]];
        }
    };

    void (^handleConnectionSuccess)(void) = ^{
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        NSLog(@"🔗 iOS is connected to %@. Waiting briefly before testing network path...", expectedSSID);
        [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:[NSString stringWithFormat:@"Connected to %@! Verifying connection...", expectedSSID ?: @"hotspot"]];
        strongSelf.pendingConnectionProbeWithoutLocation = NO;
        strongSelf.configurationReapplyCount = 0;

        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [strongSelf testConnectionToDevice:deviceIP];
        });
    };

    if (@available(iOS 14.0, *)) {
        [NEHotspotNetwork fetchCurrentWithCompletionHandler:^(NEHotspotNetwork * _Nullable currentNetwork) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (currentNetwork && [currentNetwork.SSID isEqualToString:expectedSSID]) {
                    handleConnectionSuccess();
                } else {
                    NSString *logMessage = currentNetwork ? [NSString stringWithFormat:@"📶 Currently joined WiFi: %@ (waiting for %@)", currentNetwork.SSID, expectedSSID] : @"📶 iOS has not reported a hotspot connection yet";
                    scheduleRetry(logMessage);
                }
            });
        }];
        return;
    }

    // Fallback path for iOS 13
    CFArrayRef interfaces = CNCopySupportedInterfaces();
    BOOL isConnectedToCorrectWiFi = NO;

    if (interfaces) {
        for (int i = 0; i < CFArrayGetCount(interfaces); i++) {
            CFStringRef interface = CFArrayGetValueAtIndex(interfaces, i);
            CFDictionaryRef networkInfo = CNCopyCurrentNetworkInfo(interface);

            if (networkInfo) {
                NSString *currentSSID = CFDictionaryGetValue(networkInfo, kCNNetworkInfoKeySSID);
                NSLog(@"📱 Current WiFi SSID: %@", currentSSID ?: @"(none)");

                if (currentSSID && [currentSSID isEqualToString:expectedSSID]) {
                    isConnectedToCorrectWiFi = YES;
                }

                CFRelease(networkInfo);
            }
        }
        CFRelease(interfaces);
    }

    if (isConnectedToCorrectWiFi) {
        handleConnectionSuccess();
    } else {
        scheduleRetry(@"📶 iOS has not joined the hotspot yet (fallback check)");
    }
}

- (void)reapplyHotspotConfigurationIfEligibleForAttempt:(NSInteger)attempt
                                             expectedSSID:(NSString *)expectedSSID
                                                 password:(NSString *)password
                                             maxAttempts:(NSInteger)maxAttempts {
    if (@available(iOS 11.0, *)) {
        if (self.locationPermissionDenied) {
            return;
        }
        if (expectedSSID.length == 0) {
            return;
        }
        if (attempt >= maxAttempts) {
            return;
        }
        if (attempt < 2) {
            return;
        }
        if (!(attempt == 2 || attempt == 4)) {
            return;
        }
        if (self.configurationReapplyCount >= 2) {
            return;
        }

        self.configurationReapplyCount += 1;

        NEHotspotConfiguration *retryConfiguration = nil;
        if (password.length > 0) {
            retryConfiguration = [[NEHotspotConfiguration alloc] initWithSSID:expectedSSID
                                                                   passphrase:password
                                                                       isWEP:NO];
        } else {
            retryConfiguration = [[NEHotspotConfiguration alloc] initWithSSID:expectedSSID];
        }
        retryConfiguration.joinOnce = NO;
        if (@available(iOS 13.0, *)) {
            retryConfiguration.lifeTimeInDays = @1;
        }

        NSLog(@"🔁 Re-applying NEHotspotConfiguration for %@ (reapply #%ld)", expectedSSID, (long)self.configurationReapplyCount);

        [[NEHotspotConfigurationManager sharedManager] applyConfiguration:retryConfiguration
                                                         completionHandler:^(NSError * _Nullable error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (!error || error.code == NEHotspotConfigurationErrorAlreadyAssociated) {
                    NSLog(@"✅ Hotspot configuration reapply completed (%@)", !error ? @"success" : @"already associated");
                } else {
                    NSLog(@"❌ Hotspot configuration reapply failed: %@ (code %ld)", error.localizedDescription, (long)error.code);
                }
            });
        }];
    }
}

- (void)confirmHotspotIsBroadcastingOverBluetooth:(NSString *)ssid
                                         password:(NSString *)password
                                         deviceIP:(NSString *)deviceIP
                                            retry:(NSInteger)retry {
    const NSInteger maxRetries = 3;

    NSLog(@"🔍 Verifying hotspot readiness via Bluetooth (attempt %ld/%ld)...", (long)(retry + 1), (long)maxRetries);
    __weak typeof(self) weakSelf = self;

    [QCSDKCmdCreator getDeviceWifiIPSuccess:^(NSString *confirmedIP) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        dispatch_async(dispatch_get_main_queue(), ^{
            if (confirmedIP.length > 0) {
                NSLog(@"🎉 SUCCESS: Bluetooth confirmed hotspot is active at %@", confirmedIP);
                [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:[NSString stringWithFormat:@"Hotspot %@ is live. Configuring WiFi...", ssid]];

                [strongSelf configureWiFiConnection:ssid password:password deviceIP:confirmedIP];
            } else if (retry < maxRetries - 1) {
                NSLog(@"⚠️ Hotspot still initializing, retrying in 2s...");
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                    [strongSelf confirmHotspotIsBroadcastingOverBluetooth:ssid
                                                                   password:password
                                                                   deviceIP:deviceIP
                                                                      retry:retry + 1];
                });
            } else {
                NSLog(@"❌ Hotspot never reported ready over Bluetooth after %ld attempts", (long)maxRetries);
                [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Hotspot never came online. Please retry transfer."];
            }
        });
    } failed:^{
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) { return; }

        dispatch_async(dispatch_get_main_queue(), ^{
            if (retry < maxRetries - 1) {
                NSLog(@"⚠️ Bluetooth IP fetch failed, retrying in 2s...");
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                    [strongSelf confirmHotspotIsBroadcastingOverBluetooth:ssid
                                                                   password:password
                                                                   deviceIP:deviceIP
                                                                      retry:retry + 1];
                });
            } else {
                NSLog(@"❌ Unable to confirm hotspot readiness via Bluetooth");
                [strongSelf.delegate wifiTransferManager:strongSelf didUpdateStatus:@"Failed to confirm hotspot readiness over Bluetooth. Please retry."];
            }
        });
    }];
}

- (void)testConnectionToDevice:(NSString *)deviceIP {
    // STEP 12: Test Connection - Verify actual network connectivity to glasses device
    // This tests the complete end-to-end connection: Bluetooth → WiFi Configuration → Network Path
    NSLog(@"🔗 Testing connection to specific device IP: %@", deviceIP);
    [self.delegate wifiTransferManager:self didUpdateStatus:[NSString stringWithFormat:@"Testing connection to %@...", deviceIP]];

    // Test multiple endpoints to find one that works
    NSArray *testEndpoints = @[
        @"/files/media.config",     // Main endpoint
        @"/",                       // Root endpoint
        @"/api/status",             // Status endpoint if available
        @"/config",                 // Config endpoint
        @"/info"                    // Info endpoint
    ];

    [self testEndpoints:testEndpoints forDeviceIP:deviceIP index:0];
}

- (void)testEndpoints:(NSArray *)endpoints forDeviceIP:(NSString *)deviceIP index:(NSInteger)index {
    if (index >= endpoints.count) {
        NSLog(@"❌ All endpoints failed, trying alternative IPs");
        [self.delegate wifiTransferManager:self didUpdateStatus:@"Connection failed. Trying common IPs..."];

        // Fall back to trying common IP addresses
        NSArray *possibleIPs = @[
            @"192.168.43.1", @"192.168.4.1", @"192.168.31.1", @"192.168.1.1",
            @"192.168.0.1", @"192.168.100.1", @"192.168.123.1", @"192.168.137.1",
            @"10.0.0.1", @"172.20.10.1"
        ];
        [self testIPs:possibleIPs index:0];
        return;
    }

    NSString *endpoint = endpoints[index];
    NSString *testURL = [NSString stringWithFormat:@"http://%@%@", deviceIP, endpoint];
    NSLog(@"🔍 Testing endpoint: %@", endpoint);

    NSURLRequest *request = [NSURLRequest requestWithURL:[NSURL URLWithString:testURL]
                                            cachePolicy:NSURLRequestReloadIgnoringLocalCacheData
                                        timeoutInterval:5.0];

    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request
                                                                 completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (!error && ((NSHTTPURLResponse *)response).statusCode == 200) {
                // SUCCESS: Found working endpoint
                NSLog(@"🎉 SUCCESS: Connected to glasses at %@ via %@", deviceIP, endpoint);
                [self.delegate wifiTransferManager:self didUpdateStatus:[NSString stringWithFormat:@"✅ Connected to %@! Starting download...", deviceIP]];

                // Start media download immediately
                [self startMediaDownloadFromDevice:deviceIP];
            } else {
                NSLog(@"⚠️ Endpoint %@ failed: %@", endpoint, error.localizedDescription);
                // Try next endpoint
                [self testEndpoints:endpoints forDeviceIP:deviceIP index:index + 1];
            }
        });
    }];

    [task resume];
}

- (void)testIPs:(NSArray *)ips index:(NSInteger)index {
    if (index >= ips.count) {
        [self.delegate wifiTransferManager:self didUpdateStatus:@"Could not find glasses on any known IP address"];
        return;
    }

    NSString *currentIP = ips[index];
    NSString *testURL = [NSString stringWithFormat:@"http://%@/files/media.config", currentIP];

    [self.delegate wifiTransferManager:self didUpdateStatus:[NSString stringWithFormat:@"Testing %@ (%ld/%lu)...", currentIP, (long)index + 1, (unsigned long)ips.count]];

    NSURLRequest *request = [NSURLRequest requestWithURL:[NSURL URLWithString:testURL]
                                            cachePolicy:NSURLRequestReloadIgnoringLocalCacheData
                                        timeoutInterval:3.0];

    __weak typeof(self) weakSelf = self;
    [[[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (!error && ((NSHTTPURLResponse *)response).statusCode == 200) {
                [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:[NSString stringWithFormat:@"✅ Connected to glasses at %@", currentIP]];
                NSLog(@"🎉 SUCCESS: Connected to glasses at %@", currentIP);

                // Start media download
                [weakSelf startMediaDownloadFromDevice:currentIP];

            } else {
                // Try next IP
                [weakSelf testIPs:ips index:index + 1];
            }
        });
    }] resume];
}

- (void)startMediaDownloadFromDevice:(NSString *)deviceIP {
    NSLog(@"📥 Starting media download from device at %@", deviceIP);
    [self.delegate wifiTransferManager:self didUpdateStatus:@"Starting media download..."];

    // Initialize media downloader with the device IP
    __weak typeof(self) weakSelf = self;
    self.mediaDownloader = [[GlassesMediaDownloader alloc] initWithStatusHandler:^(NSString *status, UIImage * _Nullable previewImage) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:status];
            [weakSelf.delegate wifiTransferManager:weakSelf didUpdatePreviewImage:previewImage];
        });
    }];

    [self.mediaDownloader startDownloadWithCompletion:^(NSError * _Nullable error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (error) {
                [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:[NSString stringWithFormat:@"Download failed: %@", error.localizedDescription ?: @"Unknown error"]];
            } else {
                [weakSelf.delegate wifiTransferManager:weakSelf didUpdateStatus:@"Download complete!"];
            }
            weakSelf.mediaDownloader = nil;
        });
    }];
}

@end
