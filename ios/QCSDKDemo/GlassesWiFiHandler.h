#import <Foundation/Foundation.h>
#import <NetworkExtension/NetworkExtension.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, GlassesWiFiHandlerState) {
    GlassesWiFiHandlerStateIdle,
    GlassesWiFiHandlerStateRequestingCredentials,
    GlassesWiFiHandlerStateConfiguringWiFi,
    GlassesWiFiHandlerStateConnecting,
    GlassesWiFiHandlerStateConnected,
    GlassesWiFiHandlerStateFailed
};

typedef void (^GlassesWiFiHandlerCredentialsCallback)(NSString *ssid, NSString *password, NSError * _Nullable error);
typedef void (^GlassesWiFiHandlerConnectionCallback)(BOOL success, NSString *deviceIP, NSError * _Nullable error);
typedef void (^GlassesWiFiHandlerStatusCallback)(GlassesWiFiHandlerState state, NSString *status, UIImage * _Nullable previewImage);

@interface GlassesWiFiHandler : NSObject

@property (nonatomic, readonly) GlassesWiFiHandlerState state;
@property (nonatomic, copy, readonly) NSString *currentStatus;
@property (nonatomic, copy, readonly) NSString *glassesSSID;
@property (nonatomic, copy, readonly) NSString *glassesPassword;

+ (instancetype)sharedHandler;

- (void)requestWiFiCredentialsWithStatusCallback:(GlassesWiFiHandlerStatusCallback)statusCallback
                                     completion:(GlassesWiFiHandlerCredentialsCallback)completion;

- (void)connectToGlassesWiFi:(NSString *)ssid
                    password:(NSString *)password
              statusCallback:(GlassesWiFiHandlerStatusCallback)statusCallback
                   completion:(GlassesWiFiHandlerConnectionCallback)completion;

- (void)cancelCurrentOperation;

- (void)reset;

@end

NS_ASSUME_NONNULL_END