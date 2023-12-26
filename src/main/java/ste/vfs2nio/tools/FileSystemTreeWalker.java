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

 /*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package ste.vfs2nio.tools;

import com.sshtools.vfs2nio.Vfs2NioFileSystem;
import com.sshtools.vfs2nio.Vfs2NioPath;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SIBLINGS;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import org.apache.commons.io.FilenameUtils;

/**
 * Walks a file tree, generating a sequence of events corresponding to the files
 * in the tree. "Event" in this context are calls to the given {@code FileVisitor}
 *
 * This variant from the original class provides the option to walk inside
 * archives using the virtual file system associated with the archive. The
 * supported formats are tar, tgz, tar.gz, zip, jar, gz, bz2. When a file whose
 * extension matches one of the supported formats, two events are generated:
 * first an visitFile() event, then a preVisitDirectory() event. The archive is
 * walked into until the last file, after which a postVisitDirectory() event is
 * fired. All paths in the archive share the same file system, which is closed
 * at the end of the processing.
 *
 * Directories are walked in Level Order Traversal (i.e. all siblings first,
 * order given by Files.list() results).
 *
 * Note that differently from the original implementation this Walker does not
 * prevent loops if {@code followLinks} is provided. This is for performance
 * reason due to the potentially relevant memory needed to keep in memory all
 * visited directories when traversing big file systems. Loop prevention can be
 * implemented in the given {@code FileVisitor}.
 *
 */
public class FileSystemTreeWalker implements Closeable {

    public static final String[] WALK_INTO_FILE_TYPES = new String[] {
        "bz2", "gz", "jar", "tar", "tbz2", "tgz", "zip"
    };

    public final FileVisitor<Path> visitor;
    public final Path path;
    public final int maxDepth;
    public final boolean followLinks;
    public final boolean walkIntoFiles;
    private boolean closed;
    private final LinkOption[] linkOptions;
    private final Queue<DirectoryNode> queue = new LinkedList<>();

    /**
     * The element on the walking stack corresponding to a directory node.
     */
    private static class DirectoryNode {

        private final Path dir;
        private final DirectoryStream<Path> stream;
        private final Iterator<Path> iterator;
        private boolean skipped;

        /**
         *
         * @param dir
         * @param stream can be null to indicate the directory shall be skipped
         */
        DirectoryNode(Path dir, DirectoryStream<Path> stream) {
            this.dir = dir;
            this.stream = stream;
            this.iterator = (stream == null) ? null : stream.iterator();
        }

        Path directory() {
            return dir;
        }

        DirectoryStream<Path> stream() {
            return stream;
        }

        Iterator<Path> iterator() {
            return iterator;
        }

        boolean skipped() {
            return stream == null;
        }
    }

    /**
     * Creates a {@code FileTreeWalker}.
     *
     * @param path the path to walk into
     * @param maxDepth max depth level relative to path visited (zero means do
     *                 not traverse subdirectories)
     * @param followLinks follows synonym links if true, does not follow
     * otherwise
     * @param walkInto walk into archive files if true, does not walk into
     * otherwise
     *
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     * @throws ClassCastException if {@code options} contains an element that is
     * not a {@code FileVisitOption}
     * @throws NullPointerException if {@code options} is {@code null} or the
     * options array contains a {@code null} element
     */
    public FileSystemTreeWalker(Path path, FileVisitor<Path> visitor, int maxDepth, boolean followLinks, boolean walkInto) {
        if (path == null) {
            throw new IllegalArgumentException("path can not be null");
        }
        if (visitor == null) {
            throw new IllegalArgumentException("visitor can not be null");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth can not be negative");
        }

        this.path = path;
        this.visitor = visitor;
        this.maxDepth = maxDepth;
        this.followLinks = followLinks;
        this.walkIntoFiles = walkInto;
        this.linkOptions = (followLinks) ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
        this.closed = false;
    }

    /**
     * Same as FileSystemTreeWalker(path visitor, Integer.MAX_VALUE, true, true);
     */
    public FileSystemTreeWalker(Path path, FileVisitor<Path> visitor) {
        this(path, visitor, Integer.MAX_VALUE, true, true);
    }

    /**
     * Same as FileSystemTreeWalker(path, visitor, Integer.MAX_VALUE, true, walkIntoFiles);
     */
    public FileSystemTreeWalker(Path path, FileVisitor<Path> visitor, boolean walkIntoFiles) {
        this(path, visitor, Integer.MAX_VALUE, true, walkIntoFiles);
    }

    /**
     * Visits the given file, returning the {@code Event} corresponding to that
     * visit.
     *
     * The {@code ignoreSecurityException} parameter determines whether any
     * SecurityException should be ignored or not. If a SecurityException is
     * thrown, and is ignored, then this method returns {@code null} to mean
     * that there is no event corresponding to a visit to the file.
     *
     * The {@code canUseCached} parameter determines whether cached attributes
     * for the file can be used or not.
     */
    private FileVisitResult visit(Path entry, boolean ignoreSecurityException)
    throws IOException {
        if (!Files.isDirectory(entry) || (!followLinks && Files.isSymbolicLink(entry))) {
            return visitor.visitFile(entry, null);
        }

        if ((entry.getNameCount()-path.getNameCount()) > maxDepth) {
            return FileVisitResult.CONTINUE;
        }

        // file is a directory, attempt to open it
        FileVisitResult result = CONTINUE;
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(entry);
            result = visitor.preVisitDirectory(entry, null);
        } catch (IOException ioe) {
            result = visitor.visitFileFailed(entry, ioe);
        } catch (SecurityException se) {
            if (ignoreSecurityException) {
                result = CONTINUE;
            }
            result = visitor.visitFileFailed(entry, new IOException(se));
        }

        if (skipSubtree(result) || skipSiblings(result)) {
            queue.add(new DirectoryNode(entry, null)); // do not  walk into this entry
        } else {
            queue.add(new DirectoryNode(entry, stream)); // note that stream is null in case of error
        }

        return result;
    }

    /**
     * Walk the file system from the given file
     */
    public void walk() throws IOException {
        if (closed) {
            throw new IllegalStateException("Closed");
        }

        Path entry = path;
        FileVisitResult walkingResult;

        walkingResult = visit(entry, true);
        if (keepWalking(walkingResult)) {
            entry = walkableFile(entry);
            if (entry != null) {
                walkingResult = visit(entry, true);
            }
        }

        while (!stopWalking(walkingResult)) {
            DirectoryNode top = queue.peek();
            if (top == null) {
                return;      // stack is empty, we are done
            }

            IOException ioe = null;
            Iterator<Path> iterator = top.iterator();
            walkingResult = CONTINUE;
            while (!stopWalking(walkingResult) && !skipSiblings(walkingResult) && (iterator != null) && iterator.hasNext()) {
                Path child = iterator.next();

                walkingResult = visit(child, true);
                if (keepWalking(walkingResult)) {
                    child = walkableFile(child);
                    if (child != null) {
                        walkingResult = visit(child, true);
                    }
                }
            } // no more children

            try {
                if (top.stream() != null) {
                    top.stream().close();
                }
            } catch (IOException e) {
                if (ioe == null) {
                    ioe = e;
                } else {
                    ioe.addSuppressed(e);
                }
            }
            queue.poll();
            if (!stopWalking(walkingResult) && !top.skipped()) {
                walkingResult = visitor.postVisitDirectory(top.directory(), ioe);
            }
            try {
                top.directory().getFileSystem().close();
            } catch (UnsupportedOperationException x) {
                //
                // nothing to do
                //
            }
        }
    }

    /**
     * Pops the directory node that is the current top of the stack so that
     * there are no more events for the directory (including no END_DIRECTORY)
     * event. This method is a no-op if the stack is empty or the walker is
     * closed.
     */
    void pop() {
       while (!queue.isEmpty()) {
            DirectoryNode node = queue.poll();
            try {
                if (node.stream() != null) {
                    node.stream().close();
                }
                node.directory().getFileSystem().close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Returns {@code true} if the walker is open.
     */
    boolean isOpen() {
        return !closed;
    }

    /**
     * Closes/pops all directories on the stack.
     */
    @Override
    public void close() {
        if (!closed) {
            while (!queue.isEmpty()) {
                pop();
            }
            closed = true;
        }
    }

    private boolean keepWalking(FileVisitResult result) {
        return (result == CONTINUE);
    }

    private boolean stopWalking(FileVisitResult result) {
        return (result == TERMINATE);
    }

    private boolean skipSubtree(FileVisitResult result) {
        return (result == SKIP_SUBTREE);
    }

    private boolean skipSiblings(FileVisitResult result) {
        return (result == SKIP_SIBLINGS);
    }

    private Path walkableFile(Path entry) {
        if (!walkIntoFiles) {
            return null;
        }

        String pathname = entry.toString().toLowerCase();
        if (FilenameUtils.isExtension(pathname, WALK_INTO_FILE_TYPES)) {
            String ext = FilenameUtils.getExtension(pathname);
            try {
                Vfs2NioFileSystem fs = (Vfs2NioFileSystem)FileSystems.newFileSystem(URI.create("vfs:" + ext + "://" + entry.toAbsolutePath().toString()), Collections.EMPTY_MAP);
                return new Vfs2NioPath(fs, "");
            } catch (IOException x) {}
        }

        return null;
    }
}
