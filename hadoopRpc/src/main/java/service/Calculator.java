package service;

import org.apache.hadoop.ipc.ProtocolInfo;
import proto.CalculatorPb;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
@ProtocolInfo(protocolName="service.Calculator", protocolVersion = 1L)
public interface Calculator extends CalculatorPb.CalculatorService.BlockingInterface {
}
