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
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.BDDAssertions.fail;
import org.junit.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 *
 */
public class FileSystemTreeWalkerTest {

    @Test
    public void creation() {
        FileVisitor visitor = new DummyFileVisitor();
        FileSystemTreeWalker f = new FileSystemTreeWalker(visitor, 0, false, false);

        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isZero();
        then(f.followLinks).isFalse();
        then(f.walkIntoFiles).isFalse();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(visitor, 1234, true, true);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(1234);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isTrue();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(visitor);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(Integer.MAX_VALUE);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isTrue();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(visitor, false);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(Integer.MAX_VALUE);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isFalse();
        then(f.isOpen()).isTrue();

        f = new FileSystemTreeWalker(visitor, true);
        then(f.visitor).isSameAs(visitor);
        then(f.maxDepth).isEqualTo(Integer.MAX_VALUE);
        then(f.followLinks).isTrue();
        then(f.walkIntoFiles).isTrue();
        then(f.isOpen()).isTrue();

        thenThrownBy(() -> new FileSystemTreeWalker(null, 12, true, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("visitor can not be null");

        thenThrownBy(() -> new FileSystemTreeWalker(visitor, -1, true, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxDepth can not be negative");

        thenThrownBy(() -> new FileSystemTreeWalker(null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("visitor can not be null");
    }

    @Test
    public void walking_into_with_zero_maxDepth() throws Exception {
        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(visitor, 0, true, false)) {
            f.walk(Paths.get("src/test/fs"));
        }

        then(visitor.visited).containsExactly(Paths.get("src/test/fs"));
        then(visitor.errors).isEmpty();
        then(visitor.walkedIn).isEmpty();
        then(visitor.walkedOut).isEmpty();
    }

    @Test
    public void walking_into_normal_tree() throws Exception {
        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(visitor, false)) {
            f.walk(Paths.get("src/test/fs"));
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
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(visitor)) {
            f.walk(Paths.get("src/test/fs"));
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
    public void avoid_loops() {
        fail("todo");
    }

    @Test
    public void walk_into_single_file() {
        fail("todo");
    }

    @Test
    public void walk_into_empty_directory() {
        fail("todo");
    }

    @Test
    public void skip_siblings_walking() {
        fail("todo");
    }

    @Test
    public void stop_walking() {
        fail("todo");
    }

    @Test
    public void skip_subtree() {
        fail("todo");
    }

    @Test
    public void report_errors() {
        fail("todo");
    }


    // -------------------------------------------------------- DummyFileVisitor

    private class DummyFileVisitor implements FileVisitor<Path> {
        public final List<Path> visited = new ArrayList();
        public final List<FileSystemWalkException> errors = new ArrayList();
        public final List<Path> walkedIn = new ArrayList();
        public final List<Path> walkedOut = new ArrayList();

        @Override
        public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes bfa) throws IOException {
            System.out.println("PRE: " + p.toUri());
            walkedIn.add(p); return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path p, IOException ioe) throws IOException {
            System.out.println("POST: " + p.toUri());
            walkedOut.add(p); return CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path p, BasicFileAttributes bfa) throws IOException {
            System.out.println("VISIT: " + p.toUri());
            visited.add(p); return CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path p, IOException ioe) throws IOException {
            System.out.println("ERROR: " + p.toUri());
            ioe.printStackTrace();
            errors.add(new FileSystemWalkException(p, ioe)); return CONTINUE;
        }
    }

    private Stream<URI> toUris(List<Path> paths) {
        return paths.stream().map((p) -> p.toUri());
    }
}
