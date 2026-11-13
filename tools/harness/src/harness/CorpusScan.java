/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.IContext;

/**
 * 비교 하네스 1단계: HWPX 말뭉치 스캔.
 *  1) 기존 HwpxFile 파서로 각 문서를 열어 성공/실패/시간/로그를 집계
 *  2) 파싱된 모델을 결정적인 텍스트로 덤프 (이후 단계에서 원본 vs 저장본 모델 비교에 사용)
 *  3) 대상 버전(기본 xmlVersion 1.5) 문서의 XML 요소/속성 인벤토리 작성
 *
 * 사용법: CorpusScan --root <말뭉치 폴더> --out <출력 폴더> [--target 1.5] [--timeout 120] [--no-dump]
 */
public class CorpusScan {

    private static final Pattern P_APPVER = Pattern.compile("appVersion=\"([^\"]*)\"");
    private static final Pattern P_XMLVER = Pattern.compile("xmlVersion=\"([^\"]*)\"");

    // ---- 로그 캡처 ----
    static class CaptureHandler extends Handler {
        final List<LogRecord> records = new ArrayList<LogRecord>();
        @Override public synchronized void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.INFO.intValue()) {
                records.add(r);
            }
        }
        synchronized List<LogRecord> drain() {
            List<LogRecord> l = new ArrayList<LogRecord>(records);
            records.clear();
            return l;
        }
        @Override public void flush() { }
        @Override public void close() { }
    }

    static class Agg {
        long count;
        Set<Integer> docs = new HashSet<Integer>();
        String sampleDoc;
        String sampleText;
    }

    static class Ctx implements IContext {
        HwpxFile hwpx;
        @Override public HwpxFile getHwpx() { return hwpx; }
    }

    static class Result {
        String status = "OK";
        String error = "";
        int sections;
        int paragraphs;
        long dumpNodes;
    }

    public static void main(String[] args) throws Exception {
        String root = null;
        String outDir = null;
        String target = "1.5";
        int timeoutSec = 120;
        boolean dump = true;
        for (int i = 0; i < args.length; i++) {
            if ("--root".equals(args[i])) root = args[++i];
            else if ("--out".equals(args[i])) outDir = args[++i];
            else if ("--target".equals(args[i])) target = args[++i];
            else if ("--timeout".equals(args[i])) timeoutSec = Integer.parseInt(args[++i]);
            else if ("--no-dump".equals(args[i])) dump = false;
        }
        if (root == null || outDir == null) {
            System.err.println("usage: CorpusScan --root <dir> --out <dir> [--target 1.5] [--timeout 120] [--no-dump]");
            System.exit(2);
        }

        final Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path out = Paths.get(outDir, "scan-" + stamp);
        Path dumps = out.resolve("dumps");
        Files.createDirectories(dumps);

        List<Path> files;
        try (Stream<Path> s = Files.walk(rootPath)) {
            files = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".hwpx"))
                     .sorted()
                     .collect(Collectors.toList());
        }

        // 로거 설정: 콘솔 출력 제거, 캡처 핸들러 부착
        LogManager.getLogManager().reset();
        CaptureHandler capture = new CaptureHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        rootLogger.addHandler(capture);

        ExecutorService exec = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "parse");
                t.setDaemon(true);
                return t;
            }
        });

        XmlInventory inventory = new XmlInventory();
        Coverage coverage = new Coverage();
        Map<String, Agg> logAgg = new TreeMap<String, Agg>();
        Map<String, Agg> errAgg = new TreeMap<String, Agg>();
        Map<String, Integer> statusCount = new TreeMap<String, Integer>();
        int targetDocs = 0;

        PrintWriter summary = writer(out.resolve("summary.tsv"));
        summary.println("idx\tfile\tappVersion\txmlVersion\tsizeKB\tstatus\tms\tsections\tparagraphs\tdumpNodes\tlogs\tinventoryErrors\terror");

        System.out.println("corpus: " + rootPath + "  files: " + files.size() + "  out: " + out);
        for (int idx = 0; idx < files.size(); idx++) {
            final Path p = files.get(idx);
            final String rel = rootPath.relativize(p).toString().replace('\\', '/');
            String appVer = "?";
            String xmlVer = "?";
            List<String> invErrors = new ArrayList<String>();

            // version.xml 읽기 + XML 인벤토리
            ZipFile zip = null;
            try {
                zip = openZip(p.toFile());
                String v = readEntry(zip, "version.xml");
                if (v != null) {
                    Matcher m = P_APPVER.matcher(v);
                    if (m.find()) appVer = m.group(1).split(" ")[0].replace(",", ".").replaceAll("\\.$", "");
                    m = P_XMLVER.matcher(v);
                    if (m.find()) xmlVer = m.group(1);
                }
                if (target.equals(xmlVer)) {
                    targetDocs++;
                    invErrors = inventory.scan(zip, idx, rel);
                }
            } catch (IOException e) {
                invErrors.add("zip: " + e.getMessage());
            } finally {
                if (zip != null) {
                    try { zip.close(); } catch (IOException e) { /* ignore */ }
                }
            }

            // 파싱 + 덤프
            final Path dumpFile = dump ? dumps.resolve(String.format("%03d_%s.txt", idx, sanitize(p.getFileName().toString()))) : null;
            long t0 = System.currentTimeMillis();
            capture.drain();
            final DomTracker tracker = new DomTracker();
            Future<Result> fut = exec.submit(new Callable<Result>() {
                @Override public Result call() throws Exception {
                    return parseAndDump(p.toFile(), dumpFile, tracker);
                }
            });
            Result res;
            try {
                res = fut.get(timeoutSec, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fut.cancel(true);
                res = new Result();
                res.status = "TIMEOUT";
                res.error = "timeout " + timeoutSec + "s";
            } catch (ExecutionException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                res = new Result();
                res.status = "FAIL";
                res.error = describe(c);
                aggregate(errAgg, errorKey(c), idx, rel, res.error);
            }
            long ms = System.currentTimeMillis() - t0;
            if ("OK".equals(res.status) && target.equals(xmlVer)) {
                coverage.collect(tracker, idx, rel);
            }

            List<LogRecord> logs = capture.drain();
            for (LogRecord r : logs) {
                String msg = r.getMessage() == null ? "" : r.getMessage();
                String logger = r.getLoggerName() == null ? "" : r.getLoggerName().replaceAll("^.*\\.", "");
                aggregate(logAgg, r.getLevel() + "\t" + logger + "\t" + normalize(msg), idx, rel, msg);
            }
            Integer sc = statusCount.get(res.status);
            statusCount.put(res.status, sc == null ? 1 : sc + 1);

            summary.println(idx + "\t" + rel + "\t" + appVer + "\t" + xmlVer + "\t" + (Files.size(p) / 1024) + "\t"
                    + res.status + "\t" + ms + "\t" + res.sections + "\t" + res.paragraphs + "\t" + res.dumpNodes + "\t"
                    + logs.size() + "\t" + invErrors.size() + "\t" + oneLine(res.error + (invErrors.isEmpty() ? "" : " | inv: " + invErrors.get(0))));
            summary.flush();
            System.out.println(String.format("[%3d/%d] %-7s %6dms  %s", idx + 1, files.size(), res.status, ms, rel));
        }
        summary.close();

        PrintWriter pw = writer(out.resolve("xml-elements.tsv"));
        inventory.writeElements(pw);
        pw.close();
        pw = writer(out.resolve("xml-attributes.tsv"));
        inventory.writeAttributes(pw);
        pw.close();
        pw = writer(out.resolve("zip-entries.tsv"));
        inventory.writeEntries(pw);
        pw.close();
        pw = writer(out.resolve("coverage-elements.tsv"));
        coverage.writeElements(pw);
        pw.close();
        pw = writer(out.resolve("coverage-attributes.tsv"));
        coverage.writeAttributes(pw);
        pw.close();
        writeAgg(out.resolve("parse-logs.tsv"), "level\tlogger\tmessage", logAgg);
        writeAgg(out.resolve("parse-errors.tsv"), "exception\ttopFrame", errAgg);

        PrintWriter rep = writer(out.resolve("report.txt"));
        rep.println("corpus     : " + rootPath);
        rep.println("files      : " + files.size());
        rep.println("status     : " + statusCount);
        rep.println("target xml : " + target + " (" + targetDocs + " docs used for XML inventory)");
        rep.println("inventory  : " + inventory.elementKinds() + " element kinds, " + inventory.attributeKinds() + " attribute kinds");
        rep.println("errors     : " + errAgg.size() + " distinct (see parse-errors.tsv)");
        rep.println("logs       : " + logAgg.size() + " distinct INFO+ messages (see parse-logs.tsv)");
        rep.println("coverage   : (target docs, see coverage-*.tsv)");
        rep.println(coverage.summary());
        rep.close();
        System.out.println();
        System.out.println(new String(Files.readAllBytes(out.resolve("report.txt")), StandardCharsets.UTF_8));
        exec.shutdownNow();
        System.exit(0);
    }

    static Result parseAndDump(File file, Path dumpFile, DomTracker tracker) throws Exception {
        Result res = new Result();
        Ctx ctx = new Ctx();
        HwpxFile hwpx = new TrackingHwpxFile(file, tracker);
        ctx.hwpx = hwpx;
        try {
            hwpx.open(ctx);
            res.sections = hwpx.getSections().size();
            for (HwpSection s : hwpx.getSections()) {
                res.paragraphs += s.paraList == null ? 0 : s.paraList.size();
            }
            if (dumpFile != null) {
                PrintWriter pw = writer(dumpFile);
                try {
                    ModelDumper d = new ModelDumper(pw);
                    d.dump("docInfo", hwpx.getDocInfo());
                    List<HwpSection> secs = hwpx.getSections();
                    for (int i = 0; i < secs.size(); i++) {
                        d.dump("section[" + i + "]", secs.get(i));
                    }
                    res.dumpNodes = d.getNodeCount();
                } finally {
                    pw.close();
                }
            }
        } finally {
            hwpx.close();
        }
        return res;
    }

    // ---- helpers ----

    static ZipFile openZip(File f) throws IOException {
        try {
            return new ZipFile(f, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 오래된 문서의 비 UTF-8 엔트리 이름
            return new ZipFile(f, Charset.forName("x-windows-949"));
        }
    }

    static String readEntry(ZipFile zip, String name) throws IOException {
        if (zip.getEntry(name) == null) return null;
        InputStream is = zip.getInputStream(zip.getEntry(name));
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            is.close();
        }
    }

    static PrintWriter writer(Path p) throws IOException {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(p), StandardCharsets.UTF_8)));
    }

    static String sanitize(String name) {
        String s = name.replaceAll("(?i)\\.hwpx$", "").replaceAll("[^0-9A-Za-z가-힣._-]+", "_");
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    static String normalize(String msg) {
        // 숫자/긴 문자열을 치환해 같은 유형의 메시지를 묶는다
        String s = msg.replaceAll("\"[^\"]{20,}\"", "\"…\"").replaceAll("[0-9]+", "#");
        return oneLine(s.length() > 200 ? s.substring(0, 200) : s);
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage() + " @ " + topFrame(t);
    }

    static String errorKey(Throwable t) {
        return t.getClass().getName() + "\t" + topFrame(t);
    }

    static String topFrame(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("HwpDoc.")) {
                return e.getClassName().replaceAll("^HwpDoc\\.", "") + "." + e.getMethodName() + ":" + e.getLineNumber();
            }
        }
        return t.getStackTrace().length > 0 ? t.getStackTrace()[0].toString() : "?";
    }

    static void aggregate(Map<String, Agg> map, String key, int idx, String doc, String text) {
        Agg a = map.get(key);
        if (a == null) {
            a = new Agg();
            a.sampleDoc = doc;
            a.sampleText = text;
            map.put(key, a);
        }
        a.count++;
        a.docs.add(idx);
    }

    static void writeAgg(Path p, String header, Map<String, Agg> map) throws IOException {
        List<Map.Entry<String, Agg>> list = new ArrayList<Map.Entry<String, Agg>>(map.entrySet());
        Collections.sort(list, (a, b) -> {
            int c = Integer.compare(b.getValue().docs.size(), a.getValue().docs.size());
            return c != 0 ? c : Long.compare(b.getValue().count, a.getValue().count);
        });
        PrintWriter pw = writer(p);
        pw.println("docs\tcount\t" + header + "\tsampleDoc\tsampleText");
        for (Map.Entry<String, Agg> e : list) {
            Agg a = e.getValue();
            pw.println(a.docs.size() + "\t" + a.count + "\t" + e.getKey() + "\t" + a.sampleDoc + "\t" + oneLine(a.sampleText));
        }
        pw.close();
    }
}
