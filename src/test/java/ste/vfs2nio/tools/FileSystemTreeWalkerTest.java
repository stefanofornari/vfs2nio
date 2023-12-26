/*
 * Uzz
 * ---
 *
 * Copyright (C) 2023 Stefano Fornari. Licensed under the
 * EUPL-1.2 or later (see LICENSE).
 *
 * All Rights Reserved.  No use, copying or distribution of this
 * work may be made except in accordance with a valid license
 * agreement from Stefano Fornari.  This notice must be
 * included on all copies, modifications and derivatives of this
 * work.
 *
 * STEFANO FORNARI MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY
 * OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. STEFANO FORNARI SHALL NOT BE LIABLE FOR ANY
 * DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 */
package ste.vfs2nio.tools;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SIBLINGS;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 *
 */
public class FileSystemTreeWalkerTest {

    @Rule
    public TemporaryFolder TMP = new TemporaryFolder();

    @Test
    public void creation() {
        final Path P = Paths.get(".");
        FileVisitor visitor = new DummyFileVisitor();
        FileSystemTreeWalker f = new FileSystemTreeWalker(P, visitor, 0, false, false);

        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isZero();
        then(f.followLinks).isFalse();
        then(f.walkIntoFiles).isFalse();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(P, visitor, 1234, true, true);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(1234);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isTrue();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(P, visitor);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(Integer.MAX_VALUE);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isTrue();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(P, visitor, false);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(Integer.MAX_VALUE);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isFalse();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(P, visitor, true);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(Integer.MAX_VALUE);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isTrue();
        then(f.isOpen()).isTrue();

        thenThrownBy(() -> new FileSystemTreeWalker(null, visitor, 12, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path can not be null");

        thenThrownBy(() -> new FileSystemTreeWalker(P, null, 12, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("visitor can not be null");

        thenThrownBy(() -> new FileSystemTreeWalker(P, visitor, -1, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxDepth can not be negative");

        thenThrownBy(() -> new FileSystemTreeWalker(P, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("visitor can not be null");
    }

    @Test
    public void walking_into_with_zero_maxDepth() throws Exception {
        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Paths.get("src/test/fs"), visitor, 0, true, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(
                Paths.get("src/test/fs/test.tar"),
                Paths.get("src/test/fs/test.zip"),
                Paths.get("src/test/fs/test.tgz")
        );
        then(visitor.errors).isEmpty();
        then(visitor.walkedIn).containsExactly(
                Paths.get("src/test/fs/")
        );
        then(visitor.walkedOut).containsExactly(
                Paths.get("src/test/fs/")
        );
    }

    @Test
    public void walking_into_normal_tree() throws Exception {
        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Paths.get("src/test/fs"), visitor, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(
                Paths.get("src/test/fs/test.tar"), Paths.get("src/test/fs/test.zip"),
                Paths.get("src/test/fs/test.tgz"), Paths.get("src/test/fs/dir/subdir/afile.txt")
        );
        then(visitor.errors).isEmpty();
        then(visitor.walkedIn).containsExactly(
                Paths.get("src/test/fs"), Paths.get("src/test/fs/dir"), Paths.get("src/test/fs/dir/subdir")
        );
        then(visitor.walkedOut).containsExactly(
                Paths.get("src/test/fs"), Paths.get("src/test/fs/dir"), Paths.get("src/test/fs/dir/subdir")
        );
    }

    @Test
    public void walking_into_files() throws Exception {
        final String BASEDIR = new File("").getAbsolutePath();

        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Paths.get("src/test/fs"), visitor)) {
            f.walk();
        }

        then(toUris(visitor.visited)).containsExactly(
                Paths.get("src/test/fs/test.tar").toUri(), Paths.get("src/test/fs/test.zip").toUri(),
                Paths.get("src/test/fs/test.tgz").toUri(), Paths.get("src/test/fs/dir/subdir/afile.txt").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/dir/afile.txt"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/dir/afile.txt"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/dir/afile.txt")
        );
        then(visitor.errors).isEmpty();
        then(toUris(visitor.walkedIn)).containsExactly(
                Paths.get("src/test/fs").toUri(), Paths.get("src/test/fs/dir").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/"),
                Paths.get("src/test/fs/dir/subdir").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/dir"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/dir"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/dir"),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/dir/subdir"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/dir/subdir"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/dir/subdir")
        );
        then(toUris(visitor.walkedOut)).containsExactly(
                Paths.get("src/test/fs").toUri(), Paths.get("src/test/fs/dir").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/"),
                Paths.get("src/test/fs/dir/subdir").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/dir"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/dir"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/dir"),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/test.tar!/dir/subdir"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/test.zip!/dir/subdir"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/test.tgz!/dir/subdir")
        );
    }

    @Test
    public void follow_dont_follow_links() throws IOException {
        // ---
        // Given a simple file system
        //
        Path home = TMP.newFolder("home").toPath();
        Path subdir = TMP.newFolder("home/dir/subdir").toPath();
        Path link = new File(subdir.toFile(), "link_to_home").toPath();
        // ---

        Files.createSymbolicLink(link, home);

        //
        // Follow links, maxDepth 4
        //
        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(home, visitor, 4, true, false)) {
            f.walk();
        }

        then(visitor.visited).isEmpty();
        then(toUris(visitor.walkedIn)).containsExactly(
                home.toUri(), subdir.getParent().toUri(), subdir.toUri(),
                link.toUri(), new File(link.toFile(), "dir").toURI()
        );
        then(toUris(visitor.walkedOut)).containsExactly(
                home.toUri(), subdir.getParent().toUri(), subdir.toUri(),
                link.toUri(), new File(link.toFile(), "dir").toURI()
        );
        then(visitor.errors).isEmpty();

        //
        // Do not follow links, maxDepth 4
        //
        visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(home, visitor, 4, false, false)) {
            f.walk();
        }

        then(toUris(visitor.visited)).containsExactly(
                link.toUri()
        );
        then(toUris(visitor.walkedIn)).containsExactly(
                home.toUri(), subdir.getParent().toUri(), subdir.toUri()
        );
        then(toUris(visitor.walkedOut)).containsExactly(
                home.toUri(), subdir.getParent().toUri(), subdir.toUri()
        );
        then(visitor.errors).isEmpty();
    }

    @Test
    public void walk_with_file() throws IOException {
        final Path P = Paths.get("src/test/fs/test.zip");
        final DummyFileVisitor V = new DummyFileVisitor();

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, V, 4, true, false)) {
            f.walk();
        }

        then(V.visited).containsExactly(P);
        then(V.walkedIn).isEmpty();
        then(V.walkedOut).isEmpty();
        then(V.errors).isEmpty();
    }

    @Test
    public void walk_into_single_file() throws Exception {
        final Path P = Paths.get("src/test/fs/test.zip");
        final DummyFileVisitor V = new DummyFileVisitor();

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, V, 4, true, true)) {
            f.walk();
        }

        then(toUris(V.visited)).containsExactly(P.toUri(), URI.create("vfs:zip:" + P.toUri() + "!/dir/afile.txt"));
        then(toUris(V.walkedIn)).containsExactly(
            URI.create("vfs:zip:" + P.toUri() + "!/"),
            URI.create("vfs:zip:" + P.toUri() + "!/dir"),
            URI.create("vfs:zip:" + P.toUri() + "!/dir/subdir")
        );
        then(toUris(V.walkedOut)).containsExactly(
            URI.create("vfs:zip:" + P.toUri() + "!/"),
            URI.create("vfs:zip:" + P.toUri() + "!/dir"),
            URI.create("vfs:zip:" + P.toUri() + "!/dir/subdir")
        );
        then(V.errors).isEmpty();
    }

    @Test
    public void walk_into_empty_directory() throws IOException {
        final Path P = TMP.newFolder("home").toPath();
        final DummyFileVisitor V = new DummyFileVisitor();

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, V, 4, true, true)) {
            f.walk();
        }

        then(V.visited).isEmpty();
        then(V.walkedIn).containsExactly(P);
        then(V.walkedOut).containsExactly(P);
        then(V.errors).isEmpty();
    }

    @Test
    public void skip_siblings_walking() throws IOException {
        final Path P = Paths.get("src/test/fs");
        final Path S1 = Paths.get("src/test/fs/test.tar");
        final Path S2 = Paths.get("src/test/fs/dir/subdir");
        DummyFileVisitor visitor = new DummyFileVisitor();

        //
        // Skip siblings visiting a file
        //
        visitor.checkSkipSiblings = (path) -> {
            return S1.equals(path);
        };

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, visitor, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(S1, Paths.get("src/test/fs/dir/subdir/afile.txt"));
        then(visitor.walkedIn).containsExactly(P, Paths.get("src/test/fs/dir"), Paths.get("src/test/fs/dir/subdir"));
        then(visitor.walkedOut).containsExactly(P, Paths.get("src/test/fs/dir"), Paths.get("src/test/fs/dir/subdir"));
        then(visitor.errors).isEmpty();

        //
        // Skip siblings visiting a subdir (in preVisit())
        //
        visitor = new DummyFileVisitor();
        visitor.checkSkipSiblings = (path) -> {
            return S2.equals(path);
        };

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, visitor, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(
            Paths.get("src/test/fs/test.tar"), Paths.get("src/test/fs/test.zip"), Paths.get("src/test/fs/test.tgz")
        );
        then(visitor.walkedIn).containsExactly(P, Paths.get("src/test/fs/dir"), Paths.get("src/test/fs/dir/subdir"));
        then(visitor.walkedOut).containsExactly(P, Paths.get("src/test/fs/dir"));
        then(visitor.errors).isEmpty();
    }

    @Test
    public void stop_walking() throws IOException {
        final Path P = Paths.get("src/test/fs");
        final Path S = Paths.get("src/test/fs/dir");
        final DummyFileVisitor V = new DummyFileVisitor();

        V.checkStopWalking = (path) -> {
            return S.equals(path);
        };

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, V, 4, true, false)) {
            f.walk();
        }

        then(V.visited).isEmpty();
        then(V.walkedIn).containsExactly(P, S);
        then(V.walkedOut).isEmpty();
        then(V.errors).isEmpty();
    }

    @Test
    public void skip_subtree() throws IOException {
        final Path P = Paths.get("src/test/fs");
        final Path S = Paths.get("src/test/fs/dir");
        final DummyFileVisitor V = new DummyFileVisitor();

        V.checkSkipSubtree = (path) -> {
            return S.equals(path);
        };

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, V, 4, true, false)) {
            f.walk();
        }

        then(V.visited).containsExactly(
            Paths.get("src/test/fs/test.tar"), Paths.get("src/test/fs/test.zip"),
            Paths.get("src/test/fs/test.tgz")
        );
        then(V.walkedIn).containsExactly(P, S);
        then(V.walkedOut).containsExactly(P);
        then(V.errors).isEmpty();
    }

    @Test
    public void report_errors() throws IOException {
        // ---
        // Given a simple file system
        //
        final File HOME = TMP.newFolder("fs");
        final File F1 = new File(HOME, "dir2"); F1.mkdir();
        final File F2 = new File(HOME, "dir/subdir");

        FileUtils.copyDirectory(new File("src/test/fs"), HOME);
        F1.setReadable(false); F2.setReadable(false);
        // ---

        DummyFileVisitor visitor = new DummyFileVisitor();

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(HOME.toPath(), visitor, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(
            new File(HOME, "test.tar").getAbsoluteFile().toPath(),
            new File(HOME, "test.zip").getAbsoluteFile().toPath(),
            new File(HOME, "test.tgz").getAbsoluteFile().toPath()
        );
        then(visitor.walkedIn).containsExactly(HOME.toPath(), new File(HOME, "dir").getAbsoluteFile().toPath());
        then(visitor.walkedOut).containsExactly(HOME.toPath(), new File(HOME, "dir").getAbsoluteFile().toPath());
        then(visitor.errors).hasSize(2);
        then(visitor.errors.get(0).path).isEqualTo(F1.toPath());
        then(visitor.errors.get(0).getCause()).isInstanceOf(IOException.class).hasMessage(F1.getAbsolutePath());
        then(visitor.errors.get(1).path).isEqualTo(F2.toPath());
        then(visitor.errors.get(1).getCause()).isInstanceOf(IOException.class).hasMessage(F2.getAbsolutePath());
    }

    // -------------------------------------------------------- DummyFileVisitor

    private class DummyFileVisitor implements FileVisitor<Path> {

        public final List<Path> visited = new ArrayList();
        public final List<FileSystemWalkException> errors = new ArrayList();
        public final List<Path> walkedIn = new ArrayList();
        public final List<Path> walkedOut = new ArrayList();

        public Predicate<Path> checkSkipSubtree = (path) -> { return false; };
        public Predicate<Path> checkStopWalking = checkSkipSubtree;
        public Predicate<Path> checkSkipSiblings = checkSkipSubtree;
        public Predicate<Path> checkError = checkSkipSubtree;

        @Override
        public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes bfa) throws IOException {
            System.out.println("PRE: " + p.toUri());
            walkedIn.add(p);

            if (checkStopWalking.test(p)) {
                return TERMINATE;
            }
            if (checkSkipSubtree.test(p)) {
                return SKIP_SUBTREE;
            }
            if (checkSkipSiblings.test(p)) {
                return SKIP_SIBLINGS;
            }

            return  CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path p, IOException ioe) throws IOException {
            System.out.println("POST: " + p.toUri());
            walkedOut.add(p);

            if (checkStopWalking.test(p)) {
                return TERMINATE;
            }
            if (checkSkipSiblings.test(p)) {
                return SKIP_SIBLINGS;
            }
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path p, BasicFileAttributes bfa) throws IOException {
            System.out.println("VISIT: " + p.toUri());
            visited.add(p);

            if (checkStopWalking.test(p)) {
                return TERMINATE;
            }
            if (checkSkipSiblings.test(p)) {
                return SKIP_SIBLINGS;
            }
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path p, IOException ioe) throws IOException {
            System.out.println("ERROR: " + p.toUri());
            ioe.printStackTrace();
            errors.add(new FileSystemWalkException(p, ioe));
            return CONTINUE;
        }
    }

    private Stream<URI> toUris(List<Path> paths) {
        return paths.stream().map((p) -> p.toUri());
    }
}
