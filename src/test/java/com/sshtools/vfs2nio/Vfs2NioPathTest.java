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
package com.sshtools.vfs2nio;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import static org.assertj.core.api.BDDAssertions.then;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public class Vfs2NioPathTest extends Vfs2NioTestBase {

    @Test
    public void toUri() throws IOException {
        URI uri = URI.create("vfs:file:///");
        Vfs2NioFileSystem fs = (Vfs2NioFileSystem)FileSystems.newFileSystem(uri, Collections.EMPTY_MAP);
        then(new Vfs2NioPath(fs, "", "folder", "file.txt").toUri()).isEqualTo(URI.create("vfs:file:///folder/file.txt"));
        fs.close();

        String tar = new File("src/test/fs/suite1/test.tar").getAbsolutePath();
        uri = URI.create("vfs:tar://" + tar);
        fs = (Vfs2NioFileSystem)FileSystems.newFileSystem(uri, Collections.EMPTY_MAP);
        then(new Vfs2NioPath(fs, "").toUri()).isEqualTo(URI.create("vfs:tar:file://" + tar + "!/"));
        then(new Vfs2NioPath(fs, "", "folder", "file.txt").toUri()).isEqualTo(URI.create("vfs:tar:file://" + tar + "!/folder/file.txt"));
        fs.close();
    }

    @Test
    public void toUri_encodes_special_chars() throws Exception {

        System.out.println(new URI("vfs:file:///with%20space.txt").getScheme());
        Vfs2NioFileSystem fs =
            (Vfs2NioFileSystem)FileSystems.newFileSystem(URI.create("vfs:file:///"), Collections.EMPTY_MAP);
        then(new Vfs2NioPath(fs, "", "file with spaces.txt").toUri().toString()).isEqualTo("vfs:file:///file%20with%20spaces.txt");
        then(new Vfs2NioPath(fs, "", "dir", "sub\ndir", "file with spaces.txt").toUri().toString()).isEqualTo("vfs:file:///dir/sub%0Adir/file%20with%20spaces.txt");
        fs.close();

        final String ZIP = "file://" + new File("src/test/fs/suite2/with%20space%20and%3F.zip").getAbsolutePath();
        Vfs2NioPath path = (Vfs2NioPath)Path.of(URI.create("vfs:zip:" + ZIP));
        then(path.toUri().toString()).isEqualTo("vfs:zip:" + ZIP + "!/");
    }

    @Test
    public void new_path_from_Paths() {
        URI uri = URI.create("vfs:file:///");
        Vfs2NioPath path = (Vfs2NioPath)Paths.get(uri);
        then(path.toUri()).isEqualTo(uri);
        then(path.isAbsolute()).isTrue();
        then(path.names).containsExactly("");

        String tar = new File("src/test/fs/suite1/test.tar").getAbsolutePath();
        uri = URI.create("vfs:tar://" + tar); path = (Vfs2NioPath)Paths.get(uri);
        then(path.toUri()).isEqualTo(URI.create("vfs:tar:file://" + tar + "!/"));
        then(path.isAbsolute()).isTrue();
        then(path.names).containsExactly("");
        then(path.toString()).isEqualTo("/");

        uri = URI.create("vfs:tar://" + tar + "!/dir/afile.txt"); path = (Vfs2NioPath)Paths.get(uri);
        then(path.toUri()).isEqualTo(URI.create("vfs:tar:file://" + tar + "!/dir/afile.txt"));
        then(path.isAbsolute()).isTrue();
        then(path.names).containsExactly("", "dir", "afile.txt");
        then(path.toString()).isEqualTo("/dir/afile.txt");
    }

    @Test
    public void relative_paths() throws Exception {
        final File Z = new File("src/test/fs/suite1/test.zip");
        try (
            Vfs2NioFileSystem fs = (Vfs2NioFileSystem)FileSystems.newFileSystem(URI.create("vfs:jar:" + Z.toURI()), Collections.EMPTY_MAP)
        ) {
            Vfs2NioPath p = fs.getPath("");
            then(p.isAbsolute()).isFalse(); then(p.names).isEmpty(); then(p.toString()).isEqualTo("");

            p = fs.getPath("one");
            then(p.isAbsolute()).isFalse(); then(p.names).containsExactly("one"); then(p.toString()).isEqualTo("one");

            p = fs.getPath("one/two");
            then(p.isAbsolute()).isFalse(); then(p.names).containsExactly("one", "two"); then(p.toString()).isEqualTo("one/two");

            p = fs.getPath("one/two/");
            then(p.isAbsolute()).isFalse(); then(p.names).containsExactly("one", "two"); then(p.toString()).isEqualTo("one/two");

            then(p.toUri()).isEqualTo(URI.create("vfs:jar:file://" + Z.getAbsolutePath() + "!/one/two"));
        }
    }

    @Test
    public void resolve_creates_a_subpath() throws Exception {
        //
        // starting from an absolute path
        //
        final URI uri = URI.create("vfs:file:///tmp");
        Vfs2NioPath rootPath = (Vfs2NioPath)Paths.get(uri);

        Vfs2NioPath p = rootPath.resolve("");
        then(p.isAbsolute()).isTrue(); then(p.names).containsExactlyElementsOf(rootPath.names);

        p = (Vfs2NioPath)Paths.get(uri).resolve("one");
        then(p.isAbsolute()).isTrue(); then(p.names).containsExactly("", "tmp", "one");

        p = p.resolve("two/three");
        then(p.isAbsolute()).isTrue(); then(p.names).containsExactly("", "tmp", "one", "two", "three");

        p = p.resolve("/four");
        then(p.isAbsolute()).isTrue(); then(p.names).containsExactly("", "four");

        //
        // starting from a relative path
        //
        final File F = new File(".");
        try (
            FileSystem fs = FileSystems.getFileSystem(URI.create("vfs:" + F.toURI()))
        ) {
            rootPath = (Vfs2NioPath)fs.getPath("");
            p = rootPath.resolve("");
            then(p.isAbsolute()).isFalse(); then(p.names).containsExactlyElementsOf(rootPath.names);

            p = rootPath.resolve("one");
            then(p.isAbsolute()).isFalse(); then(p.names).containsExactly("one");

            p = p.resolve("two/three");
            then(p.isAbsolute()).isFalse(); then(p.names).containsExactly("one", "two", "three");

            p = p.resolve("/four");
            then(p.isAbsolute()).isTrue(); then(p.names).containsExactly("", "four");
        }

        //
        // with a Path
        //
        rootPath = (Vfs2NioPath)Paths.get(uri);
        p = rootPath.resolve(new Vfs2NioPath(rootPath.getFileSystem(), "one", "two", "three/afile.txt"));
        then(p.names).containsExactly("", "tmp", "one", "two", "three/afile.txt");
    }

    @Test
    public void absolute_path_starts_with_root_dir() throws Exception {
        final File Z = new File("src/test/fs/suite1/test.zip");
        try (
            FileSystem fs = FileSystems.newFileSystem(URI.create("vfs:jar:" + Z.toURI()), Collections.EMPTY_MAP)
        ) {
            then(fs.getPath("dir").toAbsolutePath().toString()).isEqualTo("/dir");
        }
        then(
            Path.of(URI.create("vfs:zip:file://" + Z.getAbsolutePath()+ "!/dir")).toAbsolutePath().toString()
        ).isEqualTo("/dir");
    }

    @Test
    public void relitivize_two_paths() throws Exception {
        final String F = new File("").getAbsolutePath();
        Vfs2NioPath root = (Vfs2NioPath)Path.of(URI.create("vfs:file://" + F));
        Path child = root.getFileSystem().getPath("src/test/fs/test.tar"); // relative path

        then(root.relativize(root.resolve(child))).isEqualTo(child); // as per javadoc documentation

        root = (Vfs2NioPath)Path.of(URI.create("vfs:zip:file://" + F + "/src/test/fs/suite1/test.zip"));
        child = (Vfs2NioPath)Path.of(URI.create("vfs:zip:file://" + F + "/src/test/fs/suite1/test.zip!/dir/afile.txt"));

        then(root.relativize(child)).isEqualTo(new Vfs2NioPath(root.getFileSystem(), "dir", "afile.txt"));
    }

    @Test
    public void getParent_returns_parent_until_root() {
        Vfs2NioPath path = (Vfs2NioPath)Path.of(URI.create("vfs:file:///dir/subdir/file.txxt"));
        then(path.getParent().toString()).isEqualTo("/dir/subdir"); path = path.getParent();
        then(path.getParent().toString()).isEqualTo("/dir"); path = path.getParent();
        then(path.getParent().toString()).isEqualTo("/"); path = path.getParent();
        then(path.getParent()).isNull();
    }

    @Ignore
    public void to_string() throws Exception {
        File zip = new File("src/test/fs/suite1/test.zip");

        FileSystemManager mgr = VFS.getManager();
        System.out.println(mgr.resolveFile(zip.getAbsolutePath()).toString());
        System.out.println(zip.toString());
        System.out.println(zip.toPath().toString());
        System.out.println(zip.toURI());

        System.out.println("--- NIO");

        FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + zip.getAbsolutePath()), Collections.EMPTY_MAP);
        System.out.println(fs.getRootDirectories().iterator().next());
        System.out.println(fs.getPath("").toString());
        System.out.println(fs.getPath("dir").toString());
        System.out.println(fs.getPath("dir").toAbsolutePath().toString());
        System.out.println(fs.getPath("dir").resolve("afile.txt").toString());
        System.out.println(fs.getPath("dir").toAbsolutePath().resolve("afile.txt").toString());
        System.out.println(fs.getPath("dir").getRoot());
        System.out.println(fs.getPath("dir").toUri());

        fs = FileSystems.getDefault();
        System.out.println(fs.getPath("").resolve("src"));
        System.out.println(fs.getPath("").toAbsolutePath().resolve("src"));
        System.out.println(fs.getPath("").resolve("/src"));


        System.out.println("--- VFS");

        fs = FileSystems.newFileSystem(URI.create("vfs:jar:file:" + zip.getAbsolutePath()), Collections.EMPTY_MAP);
        System.out.println(fs.getRootDirectories().iterator().next());
        System.out.println(fs.getPath("").toString());
        System.out.println(fs.getPath("dir").toString());
        System.out.println(fs.getPath("dir").toAbsolutePath().toString());
        System.out.println(fs.getPath("dir").resolve("afile.txt").toAbsolutePath().toString());
        System.out.println(fs.getPath("dir").toAbsolutePath().resolve("afile.txt").toString());
        System.out.println(fs.getPath("dir").getRoot());
        System.out.println(fs.getPath("dir").toUri());
    }
}
