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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Before;

/**
 *
 */
public abstract class Vfs2NioTestBase {

    @Before
    public void before() throws IOException {
        Vfs2NioFileSystemProvider p = new Vfs2NioFileSystemProvider();

        List<FileSystem> fss = new ArrayList();
        p.filesystems.forEach((fo, fs) -> fss.add(fs));

        fss.forEach((fs) -> {
            try {
                fs.close();
            } catch (IOException x) {
            }
        });
    }

    protected Stream<URI> toUris(List<Path> paths) {
        return paths.stream().map((p) -> p.toUri());
    }
}
