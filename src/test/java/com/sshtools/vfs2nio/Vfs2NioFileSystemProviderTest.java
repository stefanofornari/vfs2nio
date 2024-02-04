/*
 * Vs2Nio
 * ^^^^^^
 *
 * Modifications to the original are Copyright (C) 2023 Stefano Fornari.
 * Licensed under the GUPL-1.2 or later (see LICENSE)
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class Vfs2NioFileSystemProviderTest extends Vfs2NioWithFtpTestBase {

    File rootFile = new File(File.separator);

    @Test
    public void create_and_get_file_systems() throws IOException {
        URI uri1 = URI.create("vfs:" + rootFile.toURI().toString());
        FileSystem fs = FileSystems.newFileSystem(uri1, Collections.EMPTY_MAP);

        then(fs).isNotNull().isInstanceOf(Vfs2NioFileSystem.class);

        thenThrownBy(() -> FileSystems.newFileSystem(uri1, Collections.EMPTY_MAP))
                .isInstanceOf(FileSystemAlreadyExistsException.class)
                .hasMessage("A file system for file:/// has been already created; use getFileSystem()");

        URI uri2 = URI.create("vfs:" + new File("").getAbsoluteFile().toURI().toString());
        thenThrownBy(() -> FileSystems.newFileSystem(uri2, Collections.EMPTY_MAP))
                .isInstanceOf(FileSystemAlreadyExistsException.class)
                .hasMessage("A file system for file:/// has been already created; use getFileSystem()");
        then(FileSystems.getFileSystem(uri2)).isNotNull().isInstanceOf(Vfs2NioFileSystem.class);
    }

    @Test
    public void extract_root_from_uri() throws Exception {
        Vfs2NioFileSystemProvider provider = new Vfs2NioFileSystemProvider();

        URI uri = URI.create("vfs:" + new File("").toURI());
        Vfs2NioFileSystem fs = provider.newFileSystem(uri, Collections.EMPTY_MAP);
        then(fs.getRoot().toUri()).isEqualTo(URI.create("vfs:file:///"));
        fs.close();

        String tar = new File("src/test/fs/suite1/test.tar").getAbsolutePath();
        uri = URI.create("vfs:tar://" + tar);
        fs = provider.newFileSystem(uri, Collections.EMPTY_MAP);
        then(fs.getRoot().toUri()).isEqualTo(URI.create("vfs:tar:file://" + tar + "!/"));
        fs.close();

        uri = URI.create("vfs:ftp://localhost:" + FTP.getServerControlPort() + "/public");
        fs = provider.newFileSystem(uri, Collections.EMPTY_MAP);
        then(fs.getRoot().toUri()).isEqualTo(URI.create("vfs:ftp://localhost:" + FTP.getServerControlPort() + "/"));
        fs.close();
    }

    @Test
    public void create_folder() throws Exception {
        try (FileSystem rootFs = createRootVFS()) {
            String randpath = System.getProperty("java.io.tmpdir") + "/"
                    + String.valueOf((long) (Math.abs(Math.random() * 100000)));
            File randfile = new File(randpath);
            assertFalse(randfile.exists());
            Files.createDirectory(rootFs.getPath(randpath));
            assertTrue(randfile.exists());
        }
    }

    @Test
    public void testFileDelete() throws Exception {
        try (FileSystem rootFs = createRootVFS()) {
            File file = File.createTempFile("vfs", "tmp");
            writeTestFile(file);
            Files.delete(rootFs.getPath(file.getPath()));
            assertFalse(file.exists());
        }
    }

    @Test
    public void testFileRead() throws Exception {
        try (FileSystem rootFs = createRootVFS()) {
            File file = File.createTempFile("vfs", "tmp");
            writeTestFile(file);
            try (InputStream in = Files.newInputStream(rootFs.getPath(file.getPath()))) {
                try (InputStream origIn = new FileInputStream(file)) {
                    compareStreams(origIn, in);
                }
            }
        }
    }

    @Test
    public void testFileReadUri() throws Exception {
        File file = File.createTempFile("vfs", "tmp");
        writeTestFile(file);
        Path path = Paths.get(new URI("vfs:file://" + file.getAbsolutePath()));
        try (InputStream in = Files.newInputStream(path)) {
            try (InputStream origIn = new FileInputStream(file)) {
                compareStreams(origIn, in);
            }
        }
    }

    @Test
    public void testFileWrite() throws Exception {
        try (FileSystem rootFs = createRootVFS()) {
            File file = File.createTempFile("vfs", "tmp");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            writeTestBytes(bytes);
            // Write via vfs
            try (OutputStream out = Files.newOutputStream(rootFs.getPath(file.getPath()))) {
                out.write(bytes.toByteArray());
                out.flush();
            }
            // Compare reading directly with byte array
            try (InputStream in = new FileInputStream(file)) {
                try (InputStream origIn = new ByteArrayInputStream(bytes.toByteArray())) {
                    compareStreams(origIn, in);
                }
            }
        }
    }

    @Test
    public void testRootList() throws Exception {
        try (FileSystem fs = createRootVFS()) {
            /* Get names from VFS */
            List<String> names = new ArrayList<>();
            for (Path p : fs.getRootDirectories()) {
                try (DirectoryStream<Path> d = Files.newDirectoryStream(p)) {
                    for (Path dp : d) {
                        names.add(dp.getFileName().toString());
                    }
                }
            }
            Collections.sort(names);
            /* Get names directly */
            List<String> directNames = new ArrayList<>(Arrays.asList(rootFile.list()));
            Collections.sort(directNames);
            /* Compare them */
            assertEquals(names, directNames);
        }
    }

    @Test
    public void tar_navigation() throws IOException {
        String tar = new File("src/test/fs/suite1/test.tar").getAbsolutePath();
        Path path = Paths.get(URI.create("vfs:tar://" + tar));
        then(path).isNotNull();
        then(Files.list(path).map(p -> p.toString()).toList()).containsExactly("dir");

        path = Files.list(path).findFirst().get();
        then(Files.list(path).map(p -> p.toString()).toList()).containsExactly("dir/subdir", "dir/afile.txt");
    }

    @Test
    public void tgz_navigation() throws IOException {
        String tar = new File("src/test/fs/suite1/test.tgz").getAbsolutePath();
        Path path = Paths.get(URI.create("vfs:tgz://" + tar));
        then(path).isNotNull();
        then(Files.list(path).map(p -> p.toString()).toList()).containsExactly("dir");

        path = Files.list(path).findFirst().get();
        then(Files.list(path).map(p -> p.toString()).toList()).containsExactly("dir/subdir", "dir/afile.txt");

        path.getFileSystem().close();
    }

    @Test
    public void zip_navigation() throws IOException {
        String tar = new File("src/test/fs/suite1/test.zip").getAbsolutePath();
        Path path = Paths.get(URI.create("vfs:zip://" + tar));
        then(path).isNotNull();
        then(Files.list(path).map(p -> p.toString()).toList()).containsExactly("dir");

        path = Files.list(path).findFirst().get();
        then(Files.list(path).map(p -> p.toString()).toList()).containsExactly("dir/subdir", "dir/afile.txt");
    }

    private void compareStreams(InputStream expected, InputStream actual) throws IOException {
        int r1, r2;
        while (true) {
            r1 = expected.read();
            r2 = actual.read();
            if (r1 == -1 && r2 == -1) {
                return;
            } else {
                assertEquals(r1, r2);
            }
        }
    }

    @Test
    public void all_absolute_path() throws IOException {
        Path here = Paths.get(URI.create("vfs:" + new File("").getAbsoluteFile().toURI()));

        then(here.isAbsolute()).isTrue();
        then(here).isEqualTo(here.toAbsolutePath());

        Path firstChild = Files.list(here).findFirst().get();
        then(firstChild.isAbsolute()).isTrue();
        then(firstChild).isEqualTo(firstChild.toAbsolutePath());
    }

    // --------------------------------------------------------- private methods

    private FileSystem createRootVFS() throws IOException {
        URI uri = URI.create("vfs:" + rootFile.toURI().toString());
        FileSystem fs = FileSystems.newFileSystem(uri, Collections.EMPTY_MAP);
        return fs;
    }

    private void writeTestBytes(OutputStream out) throws IOException {
        for (int i = 0; i < 1024; i++) {
            out.write((int) (Math.random() * 256));
        }
        out.flush();
    }

    private void writeTestFile(File file) throws IOException, FileNotFoundException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            writeTestBytes(fos);
        }
    }
}
