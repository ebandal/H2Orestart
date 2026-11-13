/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import HwpDoc.HwpFile;
import HwpDoc.HwpFileHeader;

/**
 * HWP 파일 헤더의 보안 플래그(암호, DRM, 공인인증서 암호화, 배포용)를 읽어 TSV 로 출력한다.
 * hwp2hwpx.ps1 이 이 목록으로 한글에서 열 수 없는(암호 입력 창이 뜨는) 문서를 건너뛴다.
 *
 * 사용법: HwpFlagScan <루트 폴더> <출력 tsv>
 */
public class HwpFlagScan {
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Path out = Paths.get(args[1]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".hwp"))
                     .sorted().collect(Collectors.toList());
        }
        int skip = 0;
        PrintWriter pw = CorpusScan.writer(out);
        pw.println("file\tpassword\tdrm\tpki\tdistributable\tskip");
        for (Path p : files) {
            String rel = root.relativize(p).toString();
            boolean pwd = false, drm = false, pki = false, dist = false;
            String note = "";
            HwpFile hwp = null;
            try {
                hwp = new HwpFile(p.toFile());
                hwp.detect();
                HwpFileHeader h = hwp.fileHeader;
                pwd = flag(h, "bPasswordEncrypted");
                drm = flag(h, "bDRMprotected");
                pki = flag(h, "bPkiEncrypted");
                dist = h.bDistributable;
            } catch (Exception e) {
                note = "detect-failed";
            } finally {
                if (hwp != null) {
                    try { hwp.close(); } catch (Exception e) { /* ignore */ }
                }
            }
            // 암호/DRM/공인인증서 암호화 문서는 한글에서 열 때 입력 창이 뜨므로 건너뛴다
            String sk = (pwd || drm || pki) ? "1" : "0";
            if ("1".equals(sk)) skip++;
            pw.println(rel + "\t" + b(pwd) + "\t" + b(drm) + "\t" + b(pki) + "\t" + b(dist) + "\t" + sk + (note.isEmpty() ? "" : "\t" + note));
        }
        pw.close();
        System.out.println("hwp files: " + files.size() + ", skip(password/drm/pki): " + skip + " -> " + out);
    }

    private static boolean flag(HwpFileHeader h, String name) throws Exception {
        Field f = HwpFileHeader.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(h);
    }

    private static String b(boolean v) {
        return v ? "1" : "0";
    }
}
