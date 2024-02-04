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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.BDDAssertions.then;
import org.junit.Test;



/**
 *
 */
public class Vfs2NioFileSystemTest {

    private Path dir = null, subdir = null, file = null;

    @Test
    public void attributes_file() throws IOException {
        Path path = vfsPath("src/test/fs/suite1/dir");
        then(Files.isDirectory(path)).isTrue();
        then(Files.isRegularFile(path)).isFalse();
        path = vfsPath("src/test/fs/suite1/dir/subdir");
        then(Files.isDirectory(path)).isTrue();
        then(Files.isRegularFile(path)).isFalse();
        path = vfsPath("src/test/fs/suite1/dir/subdir/afile.txt");
        then(Files.isDirectory(path)).isFalse();
        then(Files.isRegularFile(path)).isTrue();
    }

    @Test
    public void attributes_archive() throws IOException {
        //
        // A file in a archive is at the same time a regular file and a folder
        //
        Path path = Paths.get(URI.create("vfs:tar://" + new File("src/test/fs/suite1/test.tar").getAbsolutePath()));
        then(Files.isDirectory(path)).isTrue();
        then(Files.isRegularFile(path)).isFalse();

        ((Vfs2NioPath)path).getFileSystem().close();
    }

    // ---------------------------------------------------------- private methos

    private Path vfsPath(final String path) {
        return Path.of(
            URI.create("vfs:file://" + new File(path).getAbsolutePath())
        );
    }
}
