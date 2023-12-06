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

import java.nio.file.Paths;
import static org.assertj.core.api.BDDAssertions.fail;
import org.junit.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static ste.vfs2nio.tools.FileSystemTreeWalker.EventType.*;

/**
 *
 */
public class FileSystemTreeTest {

    @Test
    public void creation() {
        FileSystemTreeWalker f = new FileSystemTreeWalker(0, false, false);

        then(f.isOpen()).isTrue();
        then(f.maxDepth).isZero();
        then(f.followLinks).isFalse();
        then(f.walkInto).isFalse();

        f = new FileSystemTreeWalker(1234, true, true);
        then(f.isOpen()).isTrue();
        then(f.maxDepth).isEqualTo(1234);
        then(f.followLinks).isTrue();
        then(f.walkInto).isTrue();

        f = new FileSystemTreeWalker();
        then(f.isOpen()).isTrue();
        then(f.maxDepth).isZero();
        then(f.followLinks).isTrue();
        then(f.walkInto).isTrue();

        thenThrownBy(() -> new FileSystemTreeWalker(-1, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDepth can not be negative");
    }

    @Test
    public void walking_into_with_zero_maxDepth() throws Exception {

        try (FileSystemTreeWalker f = new FileSystemTreeWalker()) {
            FileSystemTreeWalker.Event e = f.walk(Paths.get("src/test/fs"));
            then(e.file()).isEqualTo(Paths.get("src/test/fs"));
            then(e.type()).isEqualTo(ENTRY);
            then(e = f.next()).isNull();
        }
    }

    @Test
    public void walking_into_normal_tree() throws Exception {
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Integer.MAX_VALUE, true, false)) {
            FileSystemTreeWalker.Event e = f.walk(Paths.get("src/test/fs"));
            then(e.file()).isEqualTo(Paths.get("src/test/fs"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir/subdir"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir/subdir/afile.txt"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir/subdir"));
            then(e.type()).isEqualTo(END_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir"));
            then(e.type()).isEqualTo(END_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.zip"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tgz"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs"));
            then(e.type()).isEqualTo(END_DIRECTORY);
        }
    }

    @Test
    public void walking_into_files() throws Exception {
        try (FileSystemTreeWalker f = new FileSystemTreeWalker(Integer.MAX_VALUE, true, true)) {
            FileSystemTreeWalker.Event e = f.walk(Paths.get("src/test/fs"));
            then(e.file()).isEqualTo(Paths.get("src/test/fs"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir/subdir"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir/subdir/afile.txt"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir/subdir"));
            then(e.type()).isEqualTo(END_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/dir"));
            then(e.type()).isEqualTo(END_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar!dir"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar!dir/afile.txt"));
            then(e.type()).isEqualTo(ENTRY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar!dir"));
            then(e.type()).isEqualTo(END_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar!dir/subdir"));
            then(e.type()).isEqualTo(START_DIRECTORY);
            e = f.next();
            then(e.file()).isEqualTo(Paths.get("src/test/fs/test.tar!dir/subdir"));
            then(e.type()).isEqualTo(END_DIRECTORY);

            // ... TODO

        }
    }

    @Test
    public void avoid_loops() {
        fail("todo");
    }
}
