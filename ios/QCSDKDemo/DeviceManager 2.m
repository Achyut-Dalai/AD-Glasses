//
//  DeviceManager.m
//  QCSDKDemo
//
//  Created by refactor on 2025/7/26.
//

#import "DeviceManager.h"
#import <QCSDK/QCSDKManager.h>
#import <QCSDK/QCSDKCmdCreator.h>

@interface DeviceManager () <QCSDKManagerDelegate>

@property (nonatomic, copy) NSString *hardwareVersion;
@property (nonatomic, copy) NSString *firmwareVersion;
@property (nonatomic, copy) NSString *wifiHardwareVersion;
@property (nonatomic, copy) NSString *wifiFirmwareVersion;
@property (nonatomic, copy) NSString *macAddress;
@property (nonatomic, assign) NSInteger batteryLevel;
@property (nonatomic, assign) BOOL isCharging;
@property (nonatomic, assign) NSInteger photoCount;
@property (nonatomic, assign) NSInteger videoCount;
@property (nonatomic, assign) NSInteger audioCount;

@end

@implementation DeviceManager

+ (instancetype)sharedManager {
    static DeviceManager *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[DeviceManager alloc] init];
    });
    return sharedInstance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        // Don't set delegates here - let ViewController handle them and forward to us
    }
    return self;
}

#pragma mark - Public Methods

- (void)startScanning {
    QCScanViewController *scanVC = [[QCScanViewController alloc] init];
    UINavigationController *navController = [[UINavigationController alloc] initWithRootViewController:scanVC];

    UIViewController *topViewController = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (topViewController.presentedViewController) {
        topViewController = topViewController.presentedViewController;
    }
    [topViewController presentViewController:navController animated:YES completion:nil];
}

- (void)stopScanning {
    [[QCCentralManager shared] stopScan];
}

- (void)disconnect {
    [[QCCentralManager shared] remove];
}

- (void)getVersionInfo {
    [QCSDKCmdCreator getDeviceVersionInfoSuccess:^(NSString * _Nonnull hdVersion, NSString * _Nonnull firmVersion, NSString * _Nonnull hdWifiVersion, NSString * _Nonnull firmWifiVersion) {
        self.hardwareVersion = hdVersion;
        self.firmwareVersion = firmVersion;
        self.wifiHardwareVersion = hdWifiVersion;
        self.wifiFirmwareVersion = firmWifiVersion;
    } fail:^{
        NSLog(@"get version fail");
    }];
}

- (void)setDeviceTime {
    [QCSDKCmdCreator setupDeviceDateTime:^(BOOL isSuccess, NSError * _Nullable err) {
        if (err) {
            NSLog(@"set time fail: %@", err.localizedDescription);
        }
    }];
}

- (void)getBatteryInfo {
    [QCSDKCmdCreator getDeviceBattery:^(NSInteger battery, BOOL charging) {
        self.batteryLevel = battery;
        self.isCharging = charging;
    } fail:^{
        NSLog(@"get battery fail");
    }];
}

- (void)getMediaInfo {
    [QCSDKCmdCreator getDeviceMedia:^(NSInteger photo, NSInteger video, NSInteger audio, NSInteger type) {
        self.photoCount = photo;
        self.videoCount = video;
        self.audioCount = audio;
    } fail:^{
        NSLog(@"get media info fail");
    }];
}

- (void)getMacAddress {
    [QCSDKCmdCreator getDeviceMacAddressSuccess:^(NSString * _Nullable macAddress) {
        self.macAddress = macAddress;
    } fail:^{
        NSLog(@"get mac address fail");
    }];
}

#pragma mark - QCCentralManagerDelegate

- (void)didState:(QCState)state {
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidUpdateConnectionState:)]) {
        [self.delegate deviceManagerDidUpdateConnectionState:state];
    }
}

- (void)didBluetoothState:(QCBluetoothState)state {
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidUpdateBluetoothState:)]) {
        [self.delegate deviceManagerDidUpdateBluetoothState:state];
    }
}

- (void)didConnected:(CBPeripheral *)peripheral {
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidConnect:)]) {
        [self.delegate deviceManagerDidConnect:peripheral];
    }
}

- (void)didDisconnecte:(CBPeripheral *)peripheral {
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidDisconnect:)]) {
        [self.delegate deviceManagerDidDisconnect:peripheral];
    }
}

- (void)didFailConnected:(CBPeripheral *)peripheral {
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidFailToConnect:)]) {
        [self.delegate deviceManagerDidFailToConnect:peripheral];
    }
}

#pragma mark - QCSDKManagerDelegate

- (void)didUpdateBatteryLevel:(NSInteger)battery charging:(BOOL)charging {
    self.batteryLevel = battery;
    self.isCharging = charging;
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidUpdateBattery:charging:)]) {
        [self.delegate deviceManagerDidUpdateBattery:battery charging:charging];
    }
}

- (void)didUpdateMediaWithPhotoCount:(NSInteger)photo videoCount:(NSInteger)video audioCount:(NSInteger)audio type:(NSInteger)type {
    self.photoCount = photo;
    self.videoCount = video;
    self.audioCount = audio;
    if ([self.delegate respondsToSelector:@selector(deviceManagerDidUpdateMediaInfo:videoCount:audioCount:type:)]) {
        [self.delegate deviceManagerDidUpdateMediaInfo:photo videoCount:video audioCount:audio type:type];
    }
}

#pragma mark - Properties

- (NSString *)deviceName {
    return [QCCentralManager shared].connectedPeripheral.name;
}

- (QCState)connectionState {
    return [QCCentralManager shared].deviceState;
}

@end