/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import HwpDoc.HwpDocInfo;
import HwpDoc.HwpFile;
import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.IContext;

/**
 * 같은 문서의 HWP / HWPX 쌍을 각각의 파서로 읽어 모델을 비교한다.
 *
 * 쌍을 찾는 방법 (둘 다 사용 가능):
 *   --mirror <hwp 폴더> <hwpx 폴더> : hwpx 폴더의 a/b.hwpx 와 hwp 폴더의 a/b.hwp (hwp2hwpx.ps1 출력 구조)
 *   --same-dir <폴더>               : 같은 폴더에 같은 이름의 .hwp / .hwpx 가 있는 경우
 *
 * 출력 (work/pairs-<시각>/):
 *   pair-summary.tsv : 문서별 결과와 차이 수
 *   pair-diffs.tsv   : 차이 키(Class.field)별 문서 수/횟수/예시 (문서 수 내림차순)
 *   docs/NNN_*.txt   : 문서별 차이 목록
 */
public class PairCompare {

    static class Pair {
        final File hwp, hwpx;
        final String name;
        Pair(File hwp, File hwpx, String name) { this.hwp = hwp; this.hwpx = hwpx; this.name = name; }
    }

    static class Agg {
        long count;
        Set<Integer> docs = new HashSet<Integer>();
        String sampleDoc, samplePath, left, right;
    }

    static class Parsed {
        Object docInfo;
        List<HwpSection> sections;
    }

    public static void main(String[] args) throws Exception {
        List<Pair> pairs = new ArrayList<Pair>();
        String outDir = "tools/harness/work";
        int timeoutSec = 120;
        int maxDiffsPerDoc = 20000;
        for (int i = 0; i < args.length; i++) {
            if ("--mirror".equals(args[i])) {
                collectMirror(Paths.get(args[++i]), Paths.get(args[++i]), pairs);
            } else if ("--same-dir".equals(args[i])) {
                collectSameDir(Paths.get(args[++i]), pairs);
            } else if ("--out".equals(args[i])) {
                outDir = args[++i];
            } else if ("--timeout".equals(args[i])) {
                timeoutSec = Integer.parseInt(args[++i]);
            }
        }
        if (pairs.isEmpty()) {
            System.err.println("usage: PairCompare [--mirror <hwpDir> <hwpxDir>] [--same-dir <dir>] [--out <dir>] [--timeout 120]");
            System.exit(2);
        }

        LogManager.getLogManager().reset();
        Path out = Paths.get(outDir, "pairs-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        Path docsDir = out.resolve("docs");
        Files.createDirectories(docsDir);
        ExecutorService exec = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "pair");
                t.setDaemon(true);
                return t;
            }
        });

        Map<String, Agg> agg = new TreeMap<String, Agg>();
        PrintWriter summary = CorpusScan.writer(out.resolve("pair-summary.tsv"));
        summary.println("idx\tfile\tstatus\tdiffs\tcompared\terror");
        Map<String, Integer> status = new TreeMap<String, Integer>();
        System.out.println("pairs: " + pairs.size() + "  out: " + out);

        for (int idx = 0; idx < pairs.size(); idx++) {
            final Pair pair = pairs.get(idx);
            String st = "OK";
            String err = "";
            ModelDiff diff = null;
            Parsed left = null, right = null;
            try {
                left = run(exec, new Callable<Parsed>() {
                    @Override public Parsed call() throws Exception { return parseHwp(pair.hwp); }
                }, timeoutSec);
            } catch (Exception e) {
                st = "HWP_FAIL";
                err = describe(e);
            }
            if (left != null) {
                try {
                    right = run(exec, new Callable<Parsed>() {
                        @Override public Parsed call() throws Exception { return parseHwpx(pair.hwpx); }
                    }, timeoutSec);
                } catch (Exception e) {
                    st = "HWPX_FAIL";
                    err = describe(e);
                }
            }
            if (left != null && right != null) {
                diff = new ModelDiff(maxDiffsPerDoc, resolver((HwpDocInfo) left.docInfo, true),
                                                     resolver((HwpDocInfo) right.docInfo, false));
                diff.compare("docInfo", left.docInfo, right.docInfo);
                diff.compare("sections", left.sections, right.sections);
                Set<String> seenInDoc = new HashSet<String>();
                PrintWriter dw = CorpusScan.writer(docsDir.resolve(String.format("%03d_%s.txt", idx, CorpusScan.sanitize(pair.name))));
                dw.println("# HWP : " + pair.hwp);
                dw.println("# HWPX: " + pair.hwpx);
                dw.println("key\tpath\thwp\thwpx");
                for (ModelDiff.Diff d : diff.getDiffs()) {
                    dw.println(d.key + "\t" + d.path + "\t" + CorpusScan.oneLine(d.left) + "\t" + CorpusScan.oneLine(d.right));
                    Agg a = agg.get(d.key);
                    if (a == null) {
                        a = new Agg();
                        a.sampleDoc = pair.name;
                        a.samplePath = d.path;
                        a.left = d.left;
                        a.right = d.right;
                        agg.put(d.key, a);
                    }
                    a.count++;
                    a.docs.add(idx);
                    seenInDoc.add(d.key);
                }
                dw.close();
                if (diff.getDiffs().size() >= maxDiffsPerDoc) st = "OK_TRUNCATED";
            }
            Integer c = status.get(st);
            status.put(st, c == null ? 1 : c + 1);
            summary.println(idx + "\t" + pair.name + "\t" + st + "\t" + (diff == null ? 0 : diff.getDiffs().size()) + "\t"
                    + (diff == null ? 0 : diff.getCompared()) + "\t" + CorpusScan.oneLine(err));
            summary.flush();
            System.out.println(String.format("[%3d/%d] %-10s diffs=%-6d %s", idx + 1, pairs.size(), st,
                    diff == null ? 0 : diff.getDiffs().size(), pair.name));
        }
        summary.close();

        List<Map.Entry<String, Agg>> list = new ArrayList<Map.Entry<String, Agg>>(agg.entrySet());
        Collections.sort(list, (x, y) -> {
            int r = Integer.compare(y.getValue().docs.size(), x.getValue().docs.size());
            return r != 0 ? r : Long.compare(y.getValue().count, x.getValue().count);
        });
        PrintWriter pw = CorpusScan.writer(out.resolve("pair-diffs.tsv"));
        pw.println("docs\tcount\tkey\tsampleDoc\tsamplePath\thwp\thwpx");
        for (Map.Entry<String, Agg> e : list) {
            Agg a = e.getValue();
            pw.println(a.docs.size() + "\t" + a.count + "\t" + e.getKey() + "\t" + a.sampleDoc + "\t" + a.samplePath + "\t"
                    + CorpusScan.oneLine(a.left) + "\t" + CorpusScan.oneLine(a.right));
        }
        pw.close();
        System.out.println();
        System.out.println("status: " + status + ", distinct diff keys: " + agg.size());
        System.out.println("see " + out.resolve("pair-diffs.tsv"));
        exec.shutdownNow();
        System.exit(0);
    }

    static <T> T run(ExecutorService exec, Callable<T> task, int timeoutSec) throws Exception {
        Future<T> f = exec.submit(task);
        try {
            return f.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new Exception("timeout " + timeoutSec + "s");
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw new Exception(c);
        }
    }

    static String describe(Throwable t) {
        return CorpusScan.describe(t);
    }

    /** ID -> 객체. 변환기(WriterContext)와 같은 방식으로 찾는다 */
    static ModelDiff.Resolver resolver(final HwpDocInfo d, final boolean hwp) {
        return new ModelDiff.Resolver() {
            @Override public Object resolve(String list, int base, Object rawId) {
                if (rawId == null) return null;
                try {
                    if ("binDataList".equals(list)) {
                        if (hwp) {      // HWP: 등록 순서의 위치
                            List<String> keys = new ArrayList<String>(d.binDataList.keySet());
                            int i = Integer.parseInt(rawId.toString());
                            return i >= 0 && i < keys.size() ? d.binDataList.get(keys.get(i)) : null;
                        }
                        return d.binDataList.get(rawId.toString());     // HWPX: 아이디
                    }
                    List<?> l;
                    switch (list) {
                    case "charShapeList":  l = d.charShapeList; break;
                    case "paraShapeList":  l = d.paraShapeList; break;
                    case "styleList":      l = d.styleList; break;
                    case "tabDefList":     l = d.tabDefList; break;
                    case "borderFillList": l = d.borderFillList; break;
                    default: return null;
                    }
                    int i = Integer.parseInt(rawId.toString()) - base;
                    return l != null && i >= 0 && i < l.size() ? l.get(i) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };
    }

    static Parsed parseHwp(File f) throws Exception {
        HwpFile hwp = new HwpFile(f);
        try {
            hwp.open();
            Parsed p = new Parsed();
            p.docInfo = hwp.getDocInfo();
            p.sections = hwp.getSections();
            return p;
        } finally {
            hwp.close();
        }
    }

    static Parsed parseHwpx(File f) throws Exception {
        final HwpxFile hwpx = new HwpxFile(f);
        try {
            hwpx.open(new IContext() {
                @Override public HwpxFile getHwpx() { return hwpx; }
            });
            Parsed p = new Parsed();
            p.docInfo = hwpx.getDocInfo();
            p.sections = hwpx.getSections();
            return p;
        } finally {
            hwpx.close();
        }
    }

    static void collectMirror(Path hwpRoot, Path hwpxRoot, List<Pair> pairs) throws Exception {
        final Path hx = hwpxRoot.toAbsolutePath().normalize();
        List<Path> files;
        try (Stream<Path> s = Files.walk(hx)) {
            files = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".hwpx"))
                     .sorted().collect(Collectors.toList());
        }
        for (Path p : files) {
            String rel = hx.relativize(p).toString();
            Path hwp = hwpRoot.resolve(rel.substring(0, rel.length() - 1));     // .hwpx -> .hwp
            if (Files.isRegularFile(hwp)) {
                pairs.add(new Pair(hwp.toFile(), p.toFile(), rel.replace('\\', '/')));
            }
        }
    }

    static void collectSameDir(Path root, List<Pair> pairs) throws Exception {
        final Path r = root.toAbsolutePath().normalize();
        Map<String, Path> hwps = new LinkedHashMap<String, Path>();
        List<Path> hwpxs = new ArrayList<Path>();
        try (Stream<Path> s = Files.walk(r)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                String n = p.toString();
                String lower = n.toLowerCase();
                if (lower.endsWith(".hwp")) hwps.put(lower.substring(0, lower.length() - 4), p);
                else if (lower.endsWith(".hwpx")) hwpxs.add(p);
            }
        }
        for (Path x : hwpxs) {
            String lower = x.toString().toLowerCase();
            Path hwp = hwps.get(lower.substring(0, lower.length() - 5));
            if (hwp != null) {
                pairs.add(new Pair(hwp.toFile(), x.toFile(), r.relativize(x).toString().replace('\\', '/')));
            }
        }
    }
}
