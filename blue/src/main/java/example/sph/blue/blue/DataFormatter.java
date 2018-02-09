package example.sph.blue.blue;

import android.support.annotation.NonNull;

/**
 * 数据格式转换工具类
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
public class DataFormatter {
    /**
     * 将字符串以前面补“0”的方式修正到指定长度
     *
     * @param s   字符串
     * @param len 指定长度
     * @return 修正后的字符串
     */
    public static String fixString2Len(@NonNull String s, int len) {
        final int strLen = s.length();
        if (strLen == len) {
            return s;
        }
        if (strLen > len) {
            return s.substring(strLen - len, strLen);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = strLen; i < len; i++) {
            sb.append("0");
        }
        return sb.append(s).toString();
    }

    /**
     * 获取字节码的16进制字符串，每个字节转换为2位字符，不足的话前面补0
     *
     * @param bytes 字节码
     * @return 16进制字符串
     */
    public static String bytes2HexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(byte2HexString(b));
        }
        return sb.toString().toUpperCase();
    }

    /**
     * 获取字节的16进制2位字符串
     * @param data 字节
     * @return 2位字符串
     */
    public static String byte2HexString(byte data){
       return fixString2Len(Integer.toHexString(data), 2);
    }

    /**
     * 将16进制字符串转换为字节码，每2位字符转换为一个字节
     * @param hexString 16进制字符串
     * @return 字节码
     */
    public static byte[] hexString2Bytes(String hexString) {
        int len = hexString.length();
        if ((len & 0b01) != 0) {
            throw new IllegalArgumentException("16进制字符串长度不是2的倍数");
        }
        len = len >> 1;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2),16);
        }
        return bytes;
    }
}
