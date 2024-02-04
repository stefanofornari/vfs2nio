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
import static org.assertj.core.api.BDDAssertions.then;
import org.junit.Test;

/**
 *
 */
public class Vfs2NioArchivePathsTest extends Vfs2NioWithFtpTestBase {

    @Test
    public void navigate_from_root() throws IOException {
        final String ROOT = "vfs:tar:file://" + new File("src/test/fs/suite1/test.tar").getAbsolutePath();

        URI uri = URI.create(ROOT);
        Path path = Path.of(uri);

        then(path.getRoot().toUri().toString()).isEqualTo(ROOT + "!/");
        then(path.getParent()).isNull();

        path = Path.of(URI.create(ROOT + "!/dir"));

        then(path.getName(0).toString()).isEqualTo("dir");
        then(path.getRoot().toUri().toString()).isEqualTo(ROOT + "!/");
        then(path.getParent()).isNotNull();
        then(path.getParent().toString()).isEqualTo("");


        then(Files.list(path).map((p) -> p.getFileName().toString())).containsExactly("subdir", "afile.txt");
    }
}
