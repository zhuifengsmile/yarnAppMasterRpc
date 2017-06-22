package service;

import org.apache.hadoop.ipc.ProtocolInfo;
import proto.EchoPb;

/**
 * Created by zhangkui.zk on 2017/6/21.
 */
@ProtocolInfo(protocolName="service.IEcho", protocolVersion = 1L)
public interface IEcho extends EchoPb.EchoService.BlockingInterface {
}
