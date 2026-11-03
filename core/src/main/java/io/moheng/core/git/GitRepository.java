package io.moheng.core.git;

import io.moheng.core.AnalysisException;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.FileChange;
import io.moheng.core.model.FileChange.LineRange;
import io.moheng.core.source.SourceSet;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GitRepository implements AutoCloseable {

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "pdf", "zip", "gz", "tar",
            "jar", "war", "ear", "class", "so", "dylib", "dll", "exe", "bin", "woff", "woff2",
            "ttf", "eot", "mp3", "mp4", "mov", "avi", "xls", "xlsx", "doc", "docx", "ppt", "pptx"
    );

    private static final Set<String> EXCLUDED_SEGMENTS = Set.of(
            ".git", "target", "build", "node_modules", ".idea", ".gradle", "out", "dist", ".mvn", ".moheng"
    );

    private final Repository repository;
    private final Path workTree;

    private GitRepository(Repository repository) {
        this.repository = repository;
        this.workTree = repository.getWorkTree().toPath().toAbsolutePath().normalize();
    }

    public static GitRepository open(Path path) {
        Path canonical = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(canonical)) {
            throw new AnalysisException("REPO_NOT_FOUND", "repository path does not exist: " + canonical);
        }
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.findGitDir(canonical.toFile()).build();
            if (repository.getDirectory() == null || !repository.getDirectory().exists()) {
                throw new AnalysisException("NOT_A_GIT_REPOSITORY", "not a git repository: " + canonical);
            }
            if (repository.isBare()) {
                throw new AnalysisException("NOT_A_GIT_REPOSITORY", "bare repository is not supported: " + canonical);
            }
            return new GitRepository(repository);
        } catch (AnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new AnalysisException("NOT_A_GIT_REPOSITORY", "cannot open git repository: " + canonical, e);
        }
    }

    public Path workTree() {
        return workTree;
    }

    public String resolveCommit(String revision) {
        try {
            ObjectId id = repository.resolve(revision + "^{commit}");
            if (id == null) {
                throw new AnalysisException("REVISION_NOT_FOUND", "revision not found: " + revision);
            }
            return id.name();
        } catch (AnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new AnalysisException("REVISION_NOT_FOUND", "cannot resolve revision: " + revision, e);
        }
    }

    public AnalysisRequest.HeadMode resolveHeadMode(AnalysisRequest request) {
        if (request.headMode() == AnalysisRequest.HeadMode.WORKTREE) {
            return AnalysisRequest.HeadMode.WORKTREE;
        }
        if (request.headMode() == AnalysisRequest.HeadMode.REV_COMMIT) {
            return AnalysisRequest.HeadMode.REV_COMMIT;
        }
        return "WORKTREE".equalsIgnoreCase(request.head())
                ? AnalysisRequest.HeadMode.WORKTREE
                : AnalysisRequest.HeadMode.REV_COMMIT;
    }

    public List<FileChange> diff(String baseRevision, String headRevision,
                                 AnalysisRequest.HeadMode headMode,
                                 AnalysisRequest.Options options) {
        String baseId = resolveCommit(baseRevision);
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            CanonicalTreeParser oldTree = treeParser(baseId);
            List<DiffEntry> entries;
            if (headMode == AnalysisRequest.HeadMode.WORKTREE) {
                entries = formatter.scan(oldTree, new FileTreeIterator(repository));
            } else {
                String headId = resolveCommit(headRevision);
                entries = formatter.scan(oldTree, treeParser(headId));
            }

            List<FileChange> changes = new ArrayList<>();
            for (DiffEntry entry : entries) {
                String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? entry.getOldPath()
                        : entry.getNewPath();
                if (isExcluded(path)) {
                    continue;
                }
                FileChange.Status status = switch (entry.getChangeType()) {
                    case ADD, COPY -> FileChange.Status.ADDED;
                    case DELETE -> FileChange.Status.DELETED;
                    case MODIFY -> FileChange.Status.MODIFIED;
                    case RENAME -> FileChange.Status.RENAMED;
                };
                if (entry.getChangeType() == DiffEntry.ChangeType.COPY) {
                    status = FileChange.Status.ADDED;
                }
                List<LineRange> ranges = ranges(formatter, entry);
                boolean binary = isBinary(path) || isBinary(entry.getOldPath());
                boolean javaFile = isJavaPath(path, options);
                String oldPath = entry.getChangeType() == DiffEntry.ChangeType.RENAME
                        ? entry.getOldPath() : null;
                changes.add(new FileChange(path, oldPath, status, ranges, binary, javaFile));
            }
            changes.sort(Comparator.comparing(FileChange::path));
            return changes;
        } catch (IOException e) {
            throw new AnalysisException("DIFF_FAILED", "git diff failed: " + e.getMessage(), e);
        }
    }

    private List<LineRange> ranges(DiffFormatter formatter, DiffEntry entry) throws IOException {
        List<LineRange> ranges = new ArrayList<>();
        var header = formatter.toFileHeader(entry);
        for (Edit edit : header.toEditList()) {
            if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                if (edit.getEndA() > edit.getBeginA()) {
                    ranges.add(new LineRange(edit.getBeginA() + 1, edit.getEndA()));
                }
            } else if (edit.getEndB() > edit.getBeginB()) {
                ranges.add(new LineRange(edit.getBeginB() + 1, edit.getEndB()));
            }
        }
        return ranges;
    }

    private CanonicalTreeParser treeParser(String commitId) throws IOException {
        RevCommit commit = repository.parseCommit(ObjectId.fromString(commitId));
        CanonicalTreeParser parser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            parser.reset(reader, commit.getTree());
        }
        return parser;
    }

    public SourceSet readAllSources(String revision, AnalysisRequest.HeadMode headMode,
                                    AnalysisRequest.Options options) {
        if (headMode == AnalysisRequest.HeadMode.WORKTREE) {
            return readWorktreeSources(null, options);
        }
        String commitId = resolveCommit(revision);
        return readRevisionSources(commitId, null, options);
    }

    public SourceSet readSources(String revision, Collection<String> onlyPaths,
                                 AnalysisRequest.HeadMode headMode,
                                 AnalysisRequest.Options options) {
        if (headMode == AnalysisRequest.HeadMode.WORKTREE) {
            return readWorktreeSources(onlyPaths, options);
        }
        String commitId = resolveCommit(revision);
        return readRevisionSources(commitId, onlyPaths, options);
    }

    private SourceSet readWorktreeSources(Collection<String> onlyPaths, AnalysisRequest.Options options) {
        Map<String, String> files = new LinkedHashMap<>();
        if (onlyPaths != null) {
            for (String path : onlyPaths) {
                Path file = workTree.resolve(path).normalize();
                if (!file.startsWith(workTree) || !Files.isRegularFile(file)) {
                    continue;
                }
                putFile(files, path, file);
            }
            return SourceSet.of(files);
        }
        try (var stream = Files.walk(workTree)) {
            stream.filter(Files::isRegularFile)
                    .map(file -> workTree.relativize(file).toString().replace('\\', '/'))
                    .filter(path -> !isExcluded(path))
                    .filter(path -> isJavaPath(path, options))
                    .sorted()
                    .forEach(path -> putFile(files, path, workTree.resolve(path)));
        } catch (IOException e) {
            throw new AnalysisException("SOURCE_READ_FAILED", "cannot scan working tree: " + e.getMessage(), e);
        }
        return SourceSet.of(files);
    }

    private void putFile(Map<String, String> files, String path, Path file) {
        try {
            bytes(files, path, Files.readAllBytes(file));
        } catch (IOException e) {
            throw new AnalysisException("SOURCE_READ_FAILED", "cannot read source file: " + path, e);
        }
    }

    private SourceSet readRevisionSources(String commitId, Collection<String> onlyPaths,
                                          AnalysisRequest.Options options) {
        Map<String, String> files = new LinkedHashMap<>();
        try {
            RevCommit commit = repository.parseCommit(ObjectId.fromString(commitId));
            if (onlyPaths == null) {
                try (TreeWalk walk = new TreeWalk(repository)) {
                    walk.addTree(commit.getTree());
                    walk.setRecursive(true);
                    while (walk.next()) {
                        String path = walk.getPathString();
                        if (isExcluded(path) || !isJavaPath(path, options)) {
                            continue;
                        }
                        ObjectLoader loader = repository.open(walk.getObjectId(0));
                        bytes(files, path, loader.getBytes());
                    }
                }
            } else {
                for (String path : onlyPaths) {
                    if (path == null || path.isBlank() || isExcluded(path)) {
                        continue;
                    }
                    try (TreeWalk walk = TreeWalk.forPath(repository, path, commit.getTree())) {
                        if (walk == null) {
                            continue;
                        }
                        ObjectLoader loader = repository.open(walk.getObjectId(0));
                        bytes(files, path, loader.getBytes());
                    }
                }
            }
        } catch (IOException e) {
            throw new AnalysisException("SOURCE_READ_FAILED", "cannot read sources at revision: " + e.getMessage(), e);
        }
        return SourceSet.of(files);
    }

    public String readFile(String revision, String path, AnalysisRequest.HeadMode headMode) {
        if (headMode == AnalysisRequest.HeadMode.WORKTREE) {
            try {
                return Files.readString(workTree.resolve(path).normalize(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AnalysisException("SOURCE_READ_FAILED", "cannot read file: " + path, e);
            }
        }
        try {
            String commitId = resolveCommit(revision);
            RevCommit commit = repository.parseCommit(ObjectId.fromString(commitId));
            try (TreeWalk walk = TreeWalk.forPath(repository, path, commit.getTree())) {
                if (walk == null) {
                    throw new AnalysisException("SOURCE_READ_FAILED", "file not found at revision: " + path);
                }
                ObjectLoader loader = repository.open(walk.getObjectId(0));
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (AnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new AnalysisException("SOURCE_READ_FAILED", "cannot read file: " + path, e);
        }
    }

    public boolean isJavaPath(String path, AnalysisRequest.Options options) {
        if (path == null || !path.endsWith(".java")) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        boolean inMain = normalized.contains("/src/main/java/") || normalized.startsWith("src/main/java/");
        boolean inTest = options.includeTests()
                && (normalized.contains("/src/test/java/") || normalized.startsWith("src/test/java/"));
        boolean customRoot = options.sourceRoots().stream()
                .anyMatch(root -> normalized.startsWith(normalizeRoot(root)));
        if (!inMain && !inTest && !customRoot) {
            return false;
        }
        String packageName = packageName(normalized);
        if (!options.includePackages().isEmpty()
                && options.includePackages().stream().noneMatch(packageName::startsWith)) {
            return false;
        }
        return options.excludePackages().stream().noneMatch(packageName::startsWith);
    }

    private String normalizeRoot(String root) {
        String normalized = root.replace('\\', '/');
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private String packageName(String path) {
        String marker = "/src/main/java/";
        int index = path.indexOf(marker);
        int shift = marker.length();
        if (index < 0) {
            marker = "/src/test/java/";
            index = path.indexOf(marker);
            shift = marker.length();
        }
        if (index < 0) {
            if (path.startsWith("src/main/java/")) {
                index = 0;
                shift = "src/main/java/".length();
            } else if (path.startsWith("src/test/java/")) {
                index = 0;
                shift = "src/test/java/".length();
            } else {
                return "";
            }
        }
        String relative = path.substring(index + shift);
        if (relative.endsWith(".java")) {
            relative = relative.substring(0, relative.length() - ".java".length());
        }
        return relative.replace('/', '.');
    }

    public boolean isExcluded(String path) {
        if (path == null) {
            return true;
        }
        String normalized = path.replace('\\', '/');
        for (String segment : EXCLUDED_SEGMENTS) {
            if (normalized.equals(segment) || normalized.startsWith(segment + "/")
                    || normalized.contains("/" + segment + "/")) {
                return true;
            }
        }
        return normalized.contains("/.git/");
    }

    private boolean isBinary(String path) {
        if (path == null) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return BINARY_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private void bytes(Map<String, String> files, String path, byte[] bytes) {
        files.put(path.replace('\\', '/'), new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        repository.close();
    }
}
