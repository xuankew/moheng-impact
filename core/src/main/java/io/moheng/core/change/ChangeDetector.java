package io.moheng.core.change;

import io.moheng.core.model.FileChange;
import io.moheng.core.model.SymbolChange;
import io.moheng.core.model.SymbolInfo;
import io.moheng.core.model.SymbolKey;
import io.moheng.core.source.AnalyzedProject;
import io.moheng.core.source.SourceSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChangeDetector {

    public List<SymbolChange> detect(AnalyzedProject base, AnalyzedProject head, List<FileChange> fileChanges) {
        Set<String> headFiles = new HashSet<>();
        Set<String> baseFiles = new HashSet<>();
        Set<String> failedHeadFiles = new HashSet<>();
        Set<String> failedBaseFiles = new HashSet<>();
        for (FileChange change : fileChanges) {
            if (!change.javaFile()) {
                continue;
            }
            headFiles.add(normalize(change.path()));
            baseFiles.add(normalize(change.basePath()));
        }
        head.parseErrors().forEach(path -> failedHeadFiles.add(normalize(path)));
        base.parseErrors().forEach(path -> failedBaseFiles.add(normalize(path)));

        List<SymbolChange> changes = new ArrayList<>();
        Set<String> changedIds = new HashSet<>();

        for (SymbolInfo headSymbol : head.symbols().values()) {
            String file = normalize(headSymbol.file());
            if (!headFiles.contains(file) || failedHeadFiles.contains(file)) {
                continue;
            }
            SymbolInfo baseSymbol = base.symbol(headSymbol.key().id());
            if (baseSymbol == null) {
                if (failedBaseFiles.contains(file)) {
                    continue;
                }
                changes.add(new SymbolChange(headSymbol.key(), SymbolChange.ChangeType.ADDED,
                        headSymbol.file(), headSymbol.line(), null,
                        headSymbol.publicDto(), headSymbol.controllerHandler(), "symbol added"));
                changedIds.add(headSymbol.key().id());
                continue;
            }
            SymbolChange change = compare(baseSymbol, headSymbol);
            if (change != null) {
                changes.add(change);
                changedIds.add(headSymbol.key().id());
            }
        }

        for (SymbolInfo baseSymbol : base.symbols().values()) {
            String file = normalize(baseSymbol.file());
            if (!baseFiles.contains(file) || failedBaseFiles.contains(file)) {
                continue;
            }
            if (head.symbol(baseSymbol.key().id()) != null) {
                continue;
            }
            if (failedHeadFiles.contains(file)) {
                continue;
            }
            changes.add(new SymbolChange(baseSymbol.key(), SymbolChange.ChangeType.REMOVED,
                    baseSymbol.file(), baseSymbol.line(), null,
                    baseSymbol.publicDto(), baseSymbol.controllerHandler(), "symbol removed"));
            changedIds.add(baseSymbol.key().id());
        }

        changes.addAll(lineRangeFallback(head, fileChanges, changedIds));

        changes.sort((left, right) -> {
            int byFile = left.file().compareTo(right.file());
            if (byFile != 0) {
                return byFile;
            }
            int byLine = Integer.compare(left.line(), right.line());
            if (byLine != 0) {
                return byLine;
            }
            return left.key().id().compareTo(right.key().id());
        });
        return changes;
    }

    private SymbolChange compare(SymbolInfo base, SymbolInfo head) {
        String id = head.key().id();
        if (!base.annotationFingerprint().equals(head.annotationFingerprint())) {
            SymbolChange.ChangeType type = head.controllerHandler()
                    ? SymbolChange.ChangeType.CONTRACT_CHANGED
                    : SymbolChange.ChangeType.ANNOTATION_CHANGED;
            return new SymbolChange(head.key(), type, head.file(), head.line(),
                    previousFile(base, head), head.publicDto(), head.controllerHandler(), "annotations changed");
        }
        if (!base.structuralFingerprint().equals(head.structuralFingerprint())) {
            SymbolChange.ChangeType type = switch (head.key().kind()) {
                case CLASS -> SymbolChange.ChangeType.STRUCTURE_CHANGED;
                case FIELD -> SymbolChange.ChangeType.STRUCTURE_CHANGED;
                case METHOD -> head.controllerHandler()
                        ? SymbolChange.ChangeType.CONTRACT_CHANGED
                        : SymbolChange.ChangeType.SIGNATURE_CHANGED;
            };
            return new SymbolChange(head.key(), type, head.file(), head.line(),
                    previousFile(base, head), head.publicDto(), head.controllerHandler(), "structure changed");
        }
        if (!base.bodyFingerprint().equals(head.bodyFingerprint())) {
            return new SymbolChange(head.key(), SymbolChange.ChangeType.MODIFIED_BODY,
                    head.file(), head.line(), previousFile(base, head),
                    head.publicDto(), head.controllerHandler(), "body changed");
        }
        if (!base.file().equals(head.file())) {
            return new SymbolChange(head.key(), SymbolChange.ChangeType.MODIFIED_BODY,
                    head.file(), head.line(), base.file(), head.publicDto(), head.controllerHandler(),
                    "file relocated");
        }
        return null;
    }

    private String previousFile(SymbolInfo base, SymbolInfo head) {
        String baseFile = normalize(base.file());
        String headFile = normalize(head.file());
        return baseFile.equals(headFile) ? null : base.file();
    }

    private List<SymbolChange> lineRangeFallback(AnalyzedProject head, List<FileChange> fileChanges,
                                                 Set<String> changedIds) {
        List<SymbolChange> fallback = new ArrayList<>();
        for (FileChange change : fileChanges) {
            if (!change.javaFile() || change.status() == FileChange.Status.DELETED
                    || change.ranges().isEmpty()) {
                continue;
            }
            String file = normalize(change.path());
            for (SymbolInfo symbol : head.symbolsInFile(file).values()) {
                if (symbol.key().kind() == SymbolKey.Kind.CLASS) {
                    continue;
                }
                if (changedIds.contains(symbol.key().id())) {
                    continue;
                }
                if (overlaps(symbol, change.ranges())) {
                    fallback.add(new SymbolChange(symbol.key(), SymbolChange.ChangeType.MODIFIED_BODY,
                            symbol.file(), symbol.line(), null, symbol.publicDto(),
                            symbol.controllerHandler(), "changed lines overlap symbol"));
                    changedIds.add(symbol.key().id());
                }
            }
        }
        return fallback;
    }

    private boolean overlaps(SymbolInfo symbol, List<FileChange.LineRange> ranges) {
        int start = symbol.line() <= 0 ? 1 : symbol.line();
        int end = symbol.endLine() < start ? start : symbol.endLine();
        for (FileChange.LineRange range : ranges) {
            if (range.start() <= end && range.end() >= start) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> changedFilePaths(List<FileChange> changes) {
        Set<String> paths = new LinkedHashSet<>();
        for (FileChange change : changes) {
            if (change.javaFile()) {
                paths.add(normalize(change.path()));
            }
        }
        return paths;
    }

    private static String normalize(String path) {
        return SourceSet.normalizePath(path == null ? "" : path);
    }
}
