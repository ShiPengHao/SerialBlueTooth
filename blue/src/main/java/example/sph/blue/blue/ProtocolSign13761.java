package example.sph.blue.blue;

/**
 * 13761协议标识
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
@SuppressWarnings({"unused"})
public class ProtocolSign13761 implements ProtocolSign {

    private static final ProtocolSign13761 instance = new ProtocolSign13761();

    private ProtocolSign13761(){}

    public static ProtocolSign13761 getInstance() {
        return instance;
    }

    @Override
    public int getSignLen() {
        return 6;
    }

    @Override
    public int getBodyLen(byte[] head) {
        return ((head[2] << 8 | head[1] & 0x3fff) >> 2) + 2;// data + cs:1 + tail:1
    }

    @Override
    public boolean checked(byte[] head) {// 68 ll lh ll lh 68
        return head[0] == 0x68 && head[5] == 0x68 && head[1] == head[3] && head[2] == head[4];
    }

    @Override
    public boolean filterHeartFrame(byte[] frame) {
        //683200320068C93412010000027D000001009016 afn02
        return frame[12] == 0x02;
    }
}
