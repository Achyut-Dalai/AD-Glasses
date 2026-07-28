//
//  QCScanViewController.m
//  QCBandSDKDemo
//
//  Created by steve on 2023/2/28.
//

#import "QCScanViewController.h"
#import "QCCentralManager.h"
#import "QCScanView.h"

@interface QCScanViewController ()<UITableViewDataSource, UITableViewDelegate,QCCentralManagerDelegate>

@property(nonatomic,strong)QCScanView *scanView;
@property(nonatomic,strong)NSArray *peripherals;
@end

@implementation QCScanViewController

- (void)viewDidLoad {
    [super viewDidLoad];

    self.title = @"Search";

    // Create and add the scan view
    self.scanView = [[QCScanView alloc] initWithFrame:self.view.bounds];
    self.scanView.tableView.delegate = self;
    self.scanView.tableView.dataSource = self;
    [self.view addSubview:self.scanView];

    [QCCentralManager shared].delegate = self;
    [[QCCentralManager shared] scan];
    [self.scanView.indicatorView startAnimating];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [[QCCentralManager shared] stopScan];
}

#pragma mark - QCCentralManagerDelegate
- (void)didScanPeripherals:(NSArray *)peripheralArr; {

    self.peripherals = peripheralArr;
    [self.scanView.tableView reloadData];
}

- (void)didState:(QCState)state {
    if(state == QCStateConnected) {
        [self.navigationController popViewControllerAnimated:true];
    }
}

- (void)didFailConnected:(CBPeripheral *)peripheral {
    NSLog(@"Connected Fial");
}

- (void)didDisconnecte:(nonnull CBPeripheral *)peripheral {
    
}


#pragma mark - Table view datasource & delegate
- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return self.peripherals.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    
    static NSString *CellIdentifier = @"Cell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:CellIdentifier];
    if (!cell) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:CellIdentifier];
    }
    
    cell.textLabel.textColor = UIColor.blueColor;
    QCBlePeripheral *per = self.peripherals[indexPath.row];
    
    cell.textLabel.text = per.peripheral.name;
    cell.detailTextLabel.text = [NSString stringWithFormat:@"%@",per.mac];
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {


    [self.scanView.indicatorView stopAnimating];
    [[QCCentralManager shared] stopScan];

    QCBlePeripheral *per= [self.peripherals objectAtIndex:indexPath.row];

    NSLog(@"Connecting to %@,mac:%@",per.peripheral.name,per.mac);

    [[QCCentralManager shared] connect:per.peripheral];
}

@end
