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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import static org.assertj.core.api.BDDAssertions.then;
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
        Vfs2NioFileSystem fs =
            (Vfs2NioFileSystem)FileSystems.newFileSystem(URI.create("vfs:file:///"), Collections.EMPTY_MAP);
        then(new Vfs2NioPath(fs, "file with spaces.txt").toUri().toString()).isEqualTo("vfs:file:///file%20with%20spaces.txt");
        then(new Vfs2NioPath(fs, "dir", "sub\ndir", "file with spaces.txt").toUri().toString()).isEqualTo("vfs:file:///dir/sub%0Adir/file%20with%20spaces.txt");
        fs.close();

        final String ZIP = "file://" + new File("src/test/fs/suite2/with%20space.zip").getAbsolutePath();
        Vfs2NioPath path = (Vfs2NioPath)Path.of(URI.create("vfs:zip:" + ZIP));
        then(path.toUri().toString()).isEqualTo("vfs:zip:" + ZIP + "!/");
    }

    @Test
    public void new_path_from_Paths() {
        URI uri = URI.create("vfs:file:///");
        then(Paths.get(uri).toUri()).isEqualTo(uri);

        String tar = new File("src/test/fs/suite1/test.tar").getAbsolutePath();
        uri = URI.create("vfs:tar://" + tar);
        then(Paths.get(uri).toUri()).isEqualTo(URI.create("vfs:tar:file://" + tar + "!/"));
    }
}
