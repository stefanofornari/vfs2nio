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

import static com.sshtools.vfs2nio.Vfs2NioWithFtpTestBase.FTP;
import static com.sshtools.vfs2nio.Vfs2NioWithFtpTestBase.HOME_DIR;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import static org.assertj.core.api.BDDAssertions.then;
import org.junit.Test;
import org.mockftpserver.fake.filesystem.FileEntry;
import ste.vfs2nio.tools.DummyFileVisitor;
import ste.vfs2nio.tools.FileSystemTreeWalker;

/**
 *
 */
public class Vfs2NioFtpPathsTest extends Vfs2NioWithFtpTestBase {

    @Test
    public void navigate_from_root() throws IOException {
        final String ROOT = "vfs:ftp://localhost:" + FTP.getServerControlPort();
        URI uri = URI.create(ROOT);
        Path path = Path.of(uri);

        then(path.getRoot().toUri().toString()).isEqualTo(ROOT + '/');
        then(path.getParent()).isNull();

        path = Path.of(URI.create(ROOT + "/public"));

        then(path.getName(0).toString()).isEqualTo("/");
        then(path.getName(1).toString()).isEqualTo("public");
        then(path.getRoot().toUri().toString()).isEqualTo(ROOT + '/');
        then(path.getParent()).isNotNull();
        then(path.getParent().toString()).isEqualTo("/");

        then(Files.list(path).map((p) -> p.getFileName().toString())).containsExactly("dir");
    }

    @Test
    public void navigate_into_archive() throws IOException {
        final FileEntry archive = new FileEntry(HOME_DIR + "/test.tar.gz");
        archive.setContents(FileUtils.readFileToByteArray(new File("src/test/fs/suite1/test.tgz")));
        FTP.getFileSystem().add(archive);

        DummyFileVisitor visitor = new DummyFileVisitor();
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Path.of(URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz")), visitor, 100, false, true)) {
            f.walk();
        }

        then(toUris(visitor.visited)).containsExactly(
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/dir/afile.txt")
        );
        then(visitor.errors).isEmpty();
        then(toUris(visitor.walkedIn)).containsExactly(
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/"),
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/dir"),
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/dir/subdir")
        );
        then(toUris(visitor.walkedOut)).containsExactly(
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/"),
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/dir"),
                URI.create("vfs:tgz:ftp://localhost:" + FTP.getServerControlPort() + "/test.tar.gz!/dir/subdir")
        );
    }
}
