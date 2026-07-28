#import "DeviceStatusCheck.h"
#import <QCSDK/QCSDKCmdCreator.h>

@interface DeviceStatusResult ()
@property (nonatomic, readwrite) BOOL isHealthy;
@property (nonatomic, readwrite, nullable) NSData *configData;
@property (nonatomic, readwrite, nullable) NSString *errorMessage;
@end

@implementation DeviceStatusResult

- (instancetype)initWithHealthy:(BOOL)isHealthy
                     configData:(nullable NSData *)configData
                    errorMessage:(nullable NSString *)errorMessage {
    self = [super init];
    if (self) {
        _isHealthy = isHealthy;
        _configData = configData;
        _errorMessage = errorMessage;
    }
    return self;
}

@end

@implementation DeviceStatusCheck

+ (void)checkDeviceStatusViaBluetooth:(DeviceStatusCheckCompletion)completion {
    NSLog(@"🔍 Checking device status via Bluetooth...");

    [QCSDKCmdCreator getDeviceConfigWithFinished:^(BOOL success, NSError * _Nullable error, id  _Nullable configData) {
        if (success && configData && [configData isKindOfClass:[NSData class]]) {
            NSData *config = (NSData *)configData;
            NSLog(@"✅ Device status check passed - config received: %lu bytes", (unsigned long)config.length);
            DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:YES
                                                                           configData:config
                                                                          errorMessage:nil];
            completion(result);
        } else {
            NSString *errorMessage = error ? error.localizedDescription : @"Unknown error";
            NSLog(@"⚠️ Device status check failed: %@", errorMessage);
            DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:NO
                                                                           configData:nil
                                                                          errorMessage:errorMessage];
            completion(result);
        }
    }];
}

+ (void)checkDeviceStatusViaWiFi:(NSString *)deviceIP
                      completion:(DeviceStatusCheckCompletion)completion {
    NSLog(@"🔍 Checking device status via WiFi at %@...", deviceIP);

    NSString *urlString = [NSString stringWithFormat:@"http://%@/api/config", deviceIP];
    NSURL *url = [NSURL URLWithString:urlString];

    if (!url) {
        NSString *error = @"Invalid device IP URL";
        NSLog(@"❌ Device status check failed: %@", error);
        DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:NO
                                                                       configData:nil
                                                                      errorMessage:error];
        completion(result);
        return;
    }

    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithURL:url
                                                             completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
        if (error) {
            NSLog(@"❌ Device status check failed: %@", error.localizedDescription);
            DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:NO
                                                                           configData:nil
                                                                          errorMessage:error.localizedDescription];
            completion(result);
            return;
        }

        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        if (!httpResponse) {
            NSString *error = @"Invalid HTTP response";
            NSLog(@"❌ Device status check failed: %@", error);
            DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:NO
                                                                           configData:nil
                                                                          errorMessage:error];
            completion(result);
            return;
        }

        if (httpResponse.statusCode == 200 && data) {
            NSLog(@"✅ Device status check passed - config received: %lu bytes", (unsigned long)data.length);
            DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:YES
                                                                           configData:data
                                                                          errorMessage:nil];
            completion(result);
        } else {
            NSString *error = [NSString stringWithFormat:@"HTTP %ld - Device not ready", (long)httpResponse.statusCode];
            NSLog(@"❌ Device status check failed: %@", error);
            DeviceStatusResult *result = [[DeviceStatusResult alloc] initWithHealthy:NO
                                                                           configData:nil
                                                                          errorMessage:error];
            completion(result);
        }
    }];

    [task resume];
}

+ (void)checkDeviceStatusWithRetry:(nullable NSString *)deviceIP
                           useWiFi:(BOOL)useWiFi
                       maxRetries:(NSInteger)maxRetries
                       retryDelay:(NSTimeInterval)retryDelay
                       completion:(DeviceStatusCheckCompletion)completion {
    [self checkWithRetryAttempt:0
                       deviceIP:deviceIP
                        useWiFi:useWiFi
                     maxRetries:maxRetries
                     retryDelay:retryDelay
                     completion:completion];
}

+ (void)checkWithRetryAttempt:(NSInteger)attempt
                     deviceIP:(nullable NSString *)deviceIP
                      useWiFi:(BOOL)useWiFi
                   maxRetries:(NSInteger)maxRetries
                   retryDelay:(NSTimeInterval)retryDelay
                   completion:(DeviceStatusCheckCompletion)completion {

    NSLog(@"🔍 Device status check attempt %ld/%ld...", (long)(attempt + 1), (long)maxRetries);

    DeviceStatusCheckCompletion checkCompletion = ^(DeviceStatusResult *result) {
        if (result.isHealthy) {
            completion(result);
        } else if (attempt < maxRetries - 1) {
            NSLog(@"⏳ Retrying device status check in %.1f seconds...", retryDelay);
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(retryDelay * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [self checkWithRetryAttempt:attempt + 1
                                  deviceIP:deviceIP
                                   useWiFi:useWiFi
                                maxRetries:maxRetries
                                retryDelay:retryDelay
                                completion:completion];
            });
        } else {
            NSLog(@"❌ Device status check failed after %ld attempts", (long)maxRetries);
            completion(result);
        }
    };

    if (useWiFi && deviceIP) {
        [self checkDeviceStatusViaWiFi:deviceIP completion:checkCompletion];
    } else {
        [self checkDeviceStatusViaBluetooth:checkCompletion];
    }
}

+ (BOOL)validateDeviceConfig:(nullable NSData *)configData {
    if (!configData || configData.length == 0) {
        return NO;
    }

    // Basic validation - check if we have a reasonable amount of config data
    // This can be enhanced based on specific device configuration format
    return configData.length >= 16; // Minimum expected config size
}

@end