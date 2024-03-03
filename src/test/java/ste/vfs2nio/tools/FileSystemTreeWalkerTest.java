/*
 * Vs2Nio
 * ^^^^^^
 *
 * Copyright (C) 2023 Stefano Fornari. Licensed under the
 * GUPL-1.2 or later (see LICENSE)
 *
 * All Rights Reserved.  No use, copying or distribution of this
 * work may be made except in accordance with a valid license
 * agreement from Stefano Fornari.  This notice must be
 * included on all copies, modifications and derivatives of this
 * work.
 *
 * THE AUTHOR MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY
 * OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. STEFANO FORNARI SHALL NOT BE LIABLE FOR ANY
 * DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 */
package ste.vfs2nio.tools;

import com.sshtools.vfs2nio.Vfs2NioTestBase;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 *
 */
public class FileSystemTreeWalkerTest extends Vfs2NioTestBase {

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            final String name = description.getMethodName();
            System.out.println(String.format("%s\n%s\n%s", "=".repeat(name.length()), name, "-".repeat(name.length())));
        }
    };

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
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Paths.get("src/test/fs/suite1"), visitor, 0, true, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(
            Paths.get("src/test/fs/suite1/test.tar"),
            Paths.get("src/test/fs/suite1/test.zip"),
            Paths.get("src/test/fs/suite1/test.tgz")
        );
        then(visitor.errors).isEmpty();
        then(visitor.walkedIn).containsExactly(
             Paths.get("src/test/fs/suite1/")
        );
        then(visitor.walkedOut).containsExactlyElementsOf(visitor.walkedIn);
    }

    @Test
    public void walking_into_normal_tree() throws Exception {
        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Paths.get("src/test/fs/suite1"), visitor, false)) {
            f.walk();
        }

        then(visitor.visited).containsExactly(
            Paths.get("src/test/fs/suite1/test.tar"), Paths.get("src/test/fs/suite1/test.zip"),
            Paths.get("src/test/fs/suite1/test.tgz"), Paths.get("src/test/fs/suite1/dir/subdir/afile.txt")
        );
        then(visitor.errors).isEmpty();
        then(visitor.walkedIn).containsExactly(
            Paths.get("src/test/fs/suite1"), Paths.get("src/test/fs/suite1/dir"), Paths.get("src/test/fs/suite1/dir/subdir")
        );
        then(visitor.walkedOut).containsExactlyElementsOf(visitor.walkedIn);
    }

    @Test
    public void walking_into_files() throws Exception {
        final String BASEDIR = new File("").getAbsolutePath();

        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Paths.get("src/test/fs/suite1"), visitor)) {
            f.walk();
        }

        then(toUris(visitor.visited)).containsExactly(
            Paths.get("src/test/fs/suite1/test.tar").toUri(), Paths.get("src/test/fs/suite1/test.zip").toUri(),
            Paths.get("src/test/fs/suite1/test.tgz").toUri(), Paths.get("src/test/fs/suite1/dir/subdir/afile.txt").toUri(),
            URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/suite1/test.tar!/dir/afile.txt"),
            URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/suite1/test.zip!/dir/afile.txt"),
            URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/suite1/test.tgz!/dir/afile.txt")
        );
        then(visitor.errors).isEmpty();
        then(toUris(visitor.walkedIn)).containsExactly(
                Paths.get("src/test/fs/suite1").toUri(), Paths.get("src/test/fs/suite1/dir").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/suite1/test.tar!/"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/suite1/test.zip!/"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/suite1/test.tgz!/"),
                Paths.get("src/test/fs/suite1/dir/subdir").toUri(),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/suite1/test.tar!/dir"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/suite1/test.zip!/dir"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/suite1/test.tgz!/dir"),
                URI.create("vfs:tar:file://" + BASEDIR + "/src/test/fs/suite1/test.tar!/dir/subdir"),
                URI.create("vfs:zip:file://" + BASEDIR + "/src/test/fs/suite1/test.zip!/dir/subdir"),
                URI.create("vfs:tgz:file://" + BASEDIR + "/src/test/fs/suite1/test.tgz!/dir/subdir")
        );
        then(visitor.walkedOut).containsExactlyElementsOf(visitor.walkedIn);
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
        final Path P = Paths.get("src/test/fs/suite1/test.zip");
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
        final Path P = Paths.get("src/test/fs/suite1/test.zip");
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
        then(V.walkedOut).containsExactlyElementsOf(V.walkedIn);
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
        final Path P = Paths.get("src/test/fs/suite1");
        final Path S1 = Paths.get("src/test/fs/suite1/test.tar");
        final Path S2 = Paths.get("src/test/fs/suite1/dir/subdir");
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

        then(visitor.visited).containsExactly(S1, Paths.get("src/test/fs/suite1/dir/subdir/afile.txt"));
        then(visitor.walkedIn).containsExactly(P, Paths.get("src/test/fs/suite1/dir"), Paths.get("src/test/fs/suite1/dir/subdir"));
        then(visitor.walkedOut).containsExactly(P, Paths.get("src/test/fs/suite1/dir"), Paths.get("src/test/fs/suite1/dir/subdir"));
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
            Paths.get("src/test/fs/suite1/test.tar"), Paths.get("src/test/fs/suite1/test.zip"), Paths.get("src/test/fs/suite1/test.tgz")
        );
        then(visitor.walkedIn).containsExactly(P, Paths.get("src/test/fs/suite1/dir"), Paths.get("src/test/fs/suite1/dir/subdir"));
        then(visitor.walkedOut).containsExactly(P, Paths.get("src/test/fs/suite1/dir"));
        then(visitor.errors).isEmpty();
    }

    @Test
    public void stop_walking() throws IOException {
        final Path P = Paths.get("src/test/fs/suite1");
        final Path S = Paths.get("src/test/fs/suite1/dir");
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
        final Path P = Paths.get("src/test/fs/suite1");
        final Path S = Paths.get("src/test/fs/suite1/dir");
        final DummyFileVisitor V = new DummyFileVisitor();

        V.checkSkipSubtree = (path) -> {
            return S.equals(path);
        };

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(P, V, 4, true, false)) {
            f.walk();
        }

        then(V.visited).containsExactly(
            Paths.get("src/test/fs/suite1/test.tar"), Paths.get("src/test/fs/suite1/test.zip"),
            Paths.get("src/test/fs/suite1/test.tgz")
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

        FileUtils.copyDirectory(new File("src/test/fs/suite1"), HOME);
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

    @Test
    public void same_DirectoryNode_iterator_once_created() throws IOException {
        FileSystemTreeWalker.DirectoryNode n = new FileSystemTreeWalker.DirectoryNode(
            Paths.get("src/test/fs/suite1"), true
        );

        Iterator<Path> i = n.iterator();
        then(n.iterator()).isSameAs(i);

        n.close();
        then(n.iterator()).isNotSameAs(i);
    }

    @Test
    public void filenames_with_special_characters() throws IOException {
        final String BASEDIR = new File("").getAbsolutePath() + '/';
        final DummyFileVisitor V = new DummyFileVisitor();

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Path.of("src/test/fs/suite2/urlencode.zip"), V, true)) {
            f.walk();
        }

        then(toUris(V.visited)).containsExactly(
            new File("src/test/fs/suite2/urlencode.zip").toURI(),
            URI.create("vfs:zip:file://" + BASEDIR + "src/test/fs/suite2/urlencode.zip!/file%20with%20spaces.txt"),
            URI.create("vfs:zip:file://" + BASEDIR + "src/test/fs/suite2/urlencode.zip!/dir/afile.txt"),
            URI.create("vfs:zip:file://" + BASEDIR + "src/test/fs/suite2/urlencode.zip!/dir/file%3Fwith%20special.txt")
        );
        then(toUris(V.walkedIn)).containsExactly(
            URI.create("vfs:zip:file://" + BASEDIR + "src/test/fs/suite2/urlencode.zip!/"),
            URI.create("vfs:zip:file://" + BASEDIR + "src/test/fs/suite2/urlencode.zip!/dir"),
            URI.create("vfs:zip:file://" + BASEDIR + "src/test/fs/suite2/urlencode.zip!/dir/subdir")
        );
        then(V.walkedOut).containsExactlyElementsOf(V.walkedIn);
        then(V.errors).isEmpty();
    }

    @Test
    public void walk_into_nested_archives() throws IOException {
        final String BASEDIR = new File("").getAbsolutePath() + '/';
        final DummyFileVisitor V = new DummyFileVisitor();

        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Path.of("src/test/fs/suite2/nested.tar"), V, true)) {
            f.walk();
        }

        then(toUris(V.visited)).containsExactly(
            new File("src/test/fs/suite2/nested.tar").toURI(),
            URI.create("vfs:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/afile.txt.gz"),
            URI.create("vfs:gz:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/afile.txt.gz!/afile.txt"),
            URI.create("vfs:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir/test.zip"),
            URI.create("vfs:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir/afile.txt"),
            URI.create("vfs:zip:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir/test.zip!/dir/afile.txt")
        );
        then(toUris(V.walkedIn)).containsExactly(
            URI.create("vfs:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/"),
            URI.create("vfs:gz:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/afile.txt.gz!/"),
            URI.create("vfs:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir"),
            URI.create("vfs:zip:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir/test.zip!/"),
            URI.create("vfs:zip:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir/test.zip!/dir"),
            URI.create("vfs:zip:tar:file://" + BASEDIR + "src/test/fs/suite2/nested.tar!/dir/test.zip!/dir/subdir")
        );
        then(V.walkedOut).containsExactlyElementsOf(V.walkedIn);
        then(V.errors).isEmpty();
    }
}