package example.sph.blue.blue;

/**
 * 协议标识：蓝牙通道尝试读取一帧数据时，用协议标识来确定接收到的数据是否满足所需数据格式以及后续需要读取的字节长度
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
public interface ProtocolSign {
    /**
     * 获取协议标识的字节长度
     * @return 协议标识长度
     */
    int getSignLen();

    /**
     * 根据协议标识的头部，确定剩余需要读取的字节长度
     * @param head 协议标识头
     * @return 剩余需要读取的字节长度
     */
    int getBodyLen(byte[] head);

    /**
     * 检查包含({@link #getSignLen})个字节的字节数组是否满足本协议的协议头规则
     * @param head 协议标识头
     * @return 是true，否则false
     */
    boolean checked(byte[] head);

    /**
     * 检查此帧是否是心跳帧（或主动上报帧，应在此）
     * @param frame 此帧全部报文
     * @return 是true，否则false
     */
    boolean filterHeartFrame(byte[] frame);
}