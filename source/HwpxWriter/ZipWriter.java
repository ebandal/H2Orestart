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
package HwpxWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 최소 zip 작성기.
 * 한글 2022 출력처럼 로컬 헤더에 크기와 CRC 를 적고 데이터 디스크립터(플래그 bit 3)와 UTF-8 플래그를 쓰지 않는다.
 * java.util.zip.ZipOutputStream 은 압축 엔트리에 데이터 디스크립터를 붙이는데,
 * 로컬 헤더만 읽는 프로그램(이 프로젝트의 OwpmlFile 등)은 이를 읽지 못한다.
 */
final class ZipWriter {
    private final OutputStream out;
    private long offset = 0;
    private final List<byte[]> central = new ArrayList<byte[]>();

    ZipWriter(OutputStream out) {
        this.out = out;
    }

    void add(String name, byte[] data, boolean compress) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(data);
        byte[] stored = compress ? deflate(data) : data;
        int method = compress ? 8 : 0;
        int time = 0, date = (2026 - 1980) << 9 | 1 << 5 | 1;     // 고정 날짜 (2026-01-01)

        ByteArrayOutputStream lh = new ByteArrayOutputStream();
        le32(lh, 0x04034b50);
        le16(lh, 20);               // version needed
        le16(lh, 0);                // flags
        le16(lh, method);
        le16(lh, time);
        le16(lh, date);
        le32(lh, (int) crc.getValue());
        le32(lh, stored.length);
        le32(lh, data.length);
        le16(lh, nameBytes.length);
        le16(lh, 0);                // extra length
        lh.write(nameBytes);
        byte[] header = lh.toByteArray();
        out.write(header);
        out.write(stored);

        ByteArrayOutputStream ch = new ByteArrayOutputStream();
        le32(ch, 0x02014b50);
        le16(ch, 20);               // version made by
        le16(ch, 20);               // version needed
        le16(ch, 0);
        le16(ch, method);
        le16(ch, time);
        le16(ch, date);
        le32(ch, (int) crc.getValue());
        le32(ch, stored.length);
        le32(ch, data.length);
        le16(ch, nameBytes.length);
        le16(ch, 0);                // extra
        le16(ch, 0);                // comment
        le16(ch, 0);                // disk
        le16(ch, 0);                // internal attr
        le32(ch, 0);                // external attr
        le32(ch, (int) offset);
        ch.write(nameBytes);
        central.add(ch.toByteArray());

        offset += header.length + stored.length;
    }

    void finish() throws IOException {
        long cdStart = offset;
        long cdSize = 0;
        for (byte[] c : central) {
            out.write(c);
            cdSize += c.length;
        }
        ByteArrayOutputStream end = new ByteArrayOutputStream();
        le32(end, 0x06054b50);
        le16(end, 0);
        le16(end, 0);
        le16(end, central.size());
        le16(end, central.size());
        le32(end, (int) cdSize);
        le32(end, (int) cdStart);
        le16(end, 0);
        out.write(end.toByteArray());
        out.flush();
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.DEFAULT_COMPRESSION, true);     // raw deflate (zip)
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2 + 64);
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            bos.write(buf, 0, n);
        }
        d.end();
        return bos.toByteArray();
    }

    private static void le16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write(v >>> 8 & 0xFF);
    }

    private static void le32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write(v >>> 8 & 0xFF);
        o.write(v >>> 16 & 0xFF);
        o.write(v >>> 24 & 0xFF);
    }
}
