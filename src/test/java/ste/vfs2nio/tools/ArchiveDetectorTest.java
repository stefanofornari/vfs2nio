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

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.BDDAssertions.then;
import org.junit.Test;

/**
 *
 */
public class ArchiveDetectorTest {

    public final static String[] SUPPORTED_ARCHIVES = new String[]{
        "tar", "tar.gz", "tgz", "zip", "jar", "gz", "bz2"
    };

    public Map<String, String> SCHEMES;

    {
        SCHEMES = new HashMap<>();
        for (String ext : SUPPORTED_ARCHIVES) {
            SCHEMES.put(ext, "tar.gz".equals(ext) ? "tgz" : ext);
        }
    }

    @Test
    public void archive_extensions() {
        for (String ext : SUPPORTED_ARCHIVES) {
            then(FileSystemTreeWalker.ArchiveDetector.uriIfArchive(Path.of(URI.create("vfs:file://test." + ext))))
                .contains(URI.create("vfs:" + SCHEMES.get(ext) + ":file:///test." + ext));
        }

        then(FileSystemTreeWalker.ArchiveDetector.uriIfArchive(Path.of(URI.create("vfs:file://test.ZIP"))))
            .contains(URI.create("vfs:zip:file:///test.ZIP"));

        then(FileSystemTreeWalker.ArchiveDetector.uriIfArchive(Path.of(URI.create("vfs:file://test.bZ2"))))
            .contains(URI.create("vfs:bz2:file:///test.bZ2"));

        then(FileSystemTreeWalker.ArchiveDetector.uriIfArchive(Path.of("/test.bZ2"))) // default faile system
            .contains(URI.create("vfs:bz2:file:///test.bZ2"));
    }

    @Test
    public void no_archive_extensions() {
        then(FileSystemTreeWalker.ArchiveDetector.uriIfArchive(
            Path.of(URI.create("vfs://file:///test.txt")
        ))).isEmpty();
        then(FileSystemTreeWalker.ArchiveDetector.uriIfArchive(
            Path.of(URI.create("vfs://file:///test.DOC")
        ))).isEmpty();
    }

}
