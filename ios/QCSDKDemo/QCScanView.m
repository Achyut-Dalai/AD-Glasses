//
//  QCScanView.m
//  QCBandSDKDemo
//
//  Created by Claude on 2025/10/14.
//

#import "QCScanView.h"

@implementation QCScanView

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        [self setupUI];
    }
    return self;
}

- (void)setupUI {
    self.backgroundColor = [UIColor whiteColor];

    // Create and configure indicator view
    _indicatorView = [[UIActivityIndicatorView alloc] initWithFrame:CGRectMake(0, 64, self.frame.size.width, 20)];
    _indicatorView.activityIndicatorViewStyle = UIActivityIndicatorViewStyleGray;
    [self addSubview:_indicatorView];

    // Create and configure table view
    _tableView = [[UITableView alloc] initWithFrame:CGRectMake(0,
                                                               CGRectGetMaxY(_indicatorView.frame),
                                                               CGRectGetWidth(self.frame),
                                                               CGRectGetHeight(self.frame) - CGRectGetMaxY(_indicatorView.frame))
                                                 style:UITableViewStylePlain];
    [self addSubview:_tableView];
}

@end