//
//  AppDelegate.m
//  QCSDKDemo
//
//  Created by steve on 2025/7/22.
//

#import "AppDelegate.h"
#import "ViewController.h"
#import "SceneDelegate.h"

@interface AppDelegate ()


@end

@implementation AppDelegate


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Override point for customization after application launch.

    if (@available(iOS 13.0, *)) {
        // SceneDelegate will set up the window for iOS 13+
        return YES;
    }

    self.window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
    self.window.backgroundColor = [UIColor whiteColor];

    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:[ViewController new]];
    nav.navigationBarHidden = NO;

    self.window.rootViewController = nav;
    [self.window makeKeyAndVisible];

    return YES;
}

@end

@implementation AppDelegate (SceneSupport)

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 130000
- (UISceneConfiguration *)application:(UIApplication *)application configurationForConnectingSceneSession:(UISceneSession *)connectingSceneSession options:(UISceneConnectionOptions *)options API_AVAILABLE(ios(13.0)) {
    UISceneConfiguration *configuration = [[UISceneConfiguration alloc] initWithName:@"Default Configuration" sessionRole:connectingSceneSession.role];
    configuration.delegateClass = [SceneDelegate class];
    return configuration;
}

- (void)application:(UIApplication *)application didDiscardSceneSessions:(NSSet<UISceneSession *> *)sceneSessions API_AVAILABLE(ios(13.0)) {
    // No resources to release when scenes are discarded in this demo.
}
#endif

@end
