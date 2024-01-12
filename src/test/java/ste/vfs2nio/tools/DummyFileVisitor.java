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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SIBLINGS;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 *
 */
public class DummyFileVisitor implements FileVisitor<Path> {

    public final List<Path> visited = new ArrayList();
    public final List<FileSystemWalkException> errors = new ArrayList();
    public final List<Path> walkedIn = new ArrayList();
    public final List<Path> walkedOut = new ArrayList();

    public Predicate<Path> checkSkipSubtree = (path) -> {
        return false;
    };
    public Predicate<Path> checkStopWalking = checkSkipSubtree;
    public Predicate<Path> checkSkipSiblings = checkSkipSubtree;
    public Predicate<Path> checkError = checkSkipSubtree;

    @Override
    public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes bfa) throws IOException {
        System.out.println("PRE: " + p.toUri());
        walkedIn.add(p);

        if (checkStopWalking.test(p)) {
            return TERMINATE;
        }
        if (checkSkipSubtree.test(p)) {
            return SKIP_SUBTREE;
        }
        if (checkSkipSiblings.test(p)) {
            return SKIP_SIBLINGS;
        }

        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path p, IOException ioe) throws IOException {
        System.out.println("POST: " + p.toUri());
        walkedOut.add(p);

        if (checkStopWalking.test(p)) {
            return TERMINATE;
        }
        if (checkSkipSiblings.test(p)) {
            return SKIP_SIBLINGS;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path p, BasicFileAttributes bfa) throws IOException {
        System.out.println("VISIT: " + p.toUri());
        visited.add(p);

        if (checkStopWalking.test(p)) {
            return TERMINATE;
        }
        if (checkSkipSiblings.test(p)) {
            return SKIP_SIBLINGS;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path p, IOException ioe) throws IOException {
        System.out.println("ERROR: " + p.toUri());
        ioe.printStackTrace();
        errors.add(new FileSystemWalkException(p, ioe));
        return SKIP_SUBTREE;
    }
}
