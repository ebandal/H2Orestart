/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */
package HwpDoc;

/**
 * java.util.Base64 대신 쓰는 순수 Java Base64 (RFC 4648 기본 알파벳).
 *
 * java.util.Base64는 JIT가 CPU 가속 코드(intrinsic)로 바꿔 실행하는데,
 * 이 가속 디코더에 메모리 손상 취약점이 있다 (CVE-2026-114938, "Java4Shell").
 * 사용자의 JRE 패치나 -XX:-UseBASE64Intrinsics 설정을 강제할 수 없으므로,
 * 문서에서 읽은 값은 이 클래스로만 디코딩해서 그 경로를 아예 타지 않게 한다.
 * 이 클래스의 메소드는 intrinsic 대상이 아니다.
 *
 * 동작은 Base64.getDecoder()와 같다: 줄바꿈 등 알파벳 밖의 문자는 거부하고,
 * 끝의 '=' 패딩은 있어도 되고 없어도 된다.
 */
public final class SafeBase64 {

    /** 문서에서 읽는 Base64 값(salt, iv, checksum 등)의 최대 길이. 이보다 길면 거부한다. */
    public static final int MAX_DECODE_INPUT = 64 * 1024;

    private static final char[] ENC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DEC = new int[128];
    static {
        java.util.Arrays.fill(DEC, -1);
        for (int i = 0; i < ENC.length; i++) {
            DEC[ENC[i]] = i;
        }
    }

    private SafeBase64() {}

    public static byte[] decode(String src) {
        if (src == null) {
            throw new IllegalArgumentException("Base64 input is null");
        }
        int len = src.length();
        if (len > MAX_DECODE_INPUT) {
            throw new IllegalArgumentException("Base64 input too long: " + len);
        }
        // 끝의 패딩 제거 (최대 2개)
        int end = len;
        int pad = 0;
        while (end > 0 && src.charAt(end - 1) == '=' && pad < 2) {
            end--;
            pad++;
        }
        if (pad > 0 && len % 4 != 0) {
            throw new IllegalArgumentException("Base64 padding is invalid");
        }
        int rem = end % 4;
        if (rem == 1) {
            throw new IllegalArgumentException("Base64 input has invalid length");
        }
        byte[] out = new byte[(end / 4) * 3 + (rem == 0 ? 0 : rem - 1)];

        int o = 0;
        int i = 0;
        int full = end - rem;
        while (i < full) {
            int b = (val(src, i) << 18) | (val(src, i + 1) << 12) | (val(src, i + 2) << 6) | val(src, i + 3);
            out[o++] = (byte) (b >> 16);
            out[o++] = (byte) (b >> 8);
            out[o++] = (byte) b;
            i += 4;
        }
        if (rem == 2) {
            int b = (val(src, i) << 18) | (val(src, i + 1) << 12);
            out[o++] = (byte) (b >> 16);
        } else if (rem == 3) {
            int b = (val(src, i) << 18) | (val(src, i + 1) << 12) | (val(src, i + 2) << 6);
            out[o++] = (byte) (b >> 16);
            out[o++] = (byte) (b >> 8);
        }
        return out;
    }

    private static int val(String s, int idx) {
        char c = s.charAt(idx);
        int v = c < 128 ? DEC[c] : -1;
        if (v < 0) {
            throw new IllegalArgumentException("Illegal base64 character " + Integer.toHexString(c));
        }
        return v;
    }

    public static String encodeToString(byte[] src) {
        int n = src.length;
        char[] out = new char[((n + 2) / 3) * 4];
        int o = 0;
        int i = 0;
        int full = n - n % 3;
        while (i < full) {
            int b = ((src[i] & 0xFF) << 16) | ((src[i + 1] & 0xFF) << 8) | (src[i + 2] & 0xFF);
            out[o++] = ENC[(b >> 18) & 0x3F];
            out[o++] = ENC[(b >> 12) & 0x3F];
            out[o++] = ENC[(b >> 6) & 0x3F];
            out[o++] = ENC[b & 0x3F];
            i += 3;
        }
        int rem = n - full;
        if (rem == 1) {
            int b = (src[i] & 0xFF) << 16;
            out[o++] = ENC[(b >> 18) & 0x3F];
            out[o++] = ENC[(b >> 12) & 0x3F];
            out[o++] = '=';
            out[o++] = '=';
        } else if (rem == 2) {
            int b = ((src[i] & 0xFF) << 16) | ((src[i + 1] & 0xFF) << 8);
            out[o++] = ENC[(b >> 18) & 0x3F];
            out[o++] = ENC[(b >> 12) & 0x3F];
            out[o++] = ENC[(b >> 6) & 0x3F];
            out[o++] = '=';
        }
        return new String(out);
    }
}
