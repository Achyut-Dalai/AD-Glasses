//
//  SceneDelegate.m
//  QCSDKDemo
//
//  Created by Codex on 2024/10/03.
//

#import "SceneDelegate.h"
#import "ViewController.h"

API_AVAILABLE(ios(13.0))
@implementation SceneDelegate

- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)connectionOptions {
    if (![scene isKindOfClass:[UIWindowScene class]]) {
        return;
    }
    UIWindowScene *windowScene = (UIWindowScene *)scene;
    self.window = [[UIWindow alloc] initWithWindowScene:windowScene];
    self.window.backgroundColor = [UIColor whiteColor];

    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:[ViewController new]];
    nav.navigationBarHidden = NO;

    self.window.rootViewController = nav;
    [self.window makeKeyAndVisible];
}

@end
