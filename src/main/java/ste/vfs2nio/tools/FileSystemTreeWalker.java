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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

/**
 * Walks a file tree, generating a sequence of events corresponding to the files
 * in the tree. "Event" in this context are calls to the given {@code FileVisitor}
 *
 * This variant from the original class provides the option to walk inside
 * archives using the virtual file system associated with the archive. The
 * supported formats are tar, tar.gz, tgz, zip, jar, gz, bz2. When a file whose
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

    public final FileVisitor<Path> visitor;
    public final Path path;
    public final int maxDepth;
    public final boolean followLinks;
    public final boolean walkIntoFiles;
    private boolean closed;
    private final Queue<DirectoryNode> queue = new LinkedList<>();

    /**
     * The element on the walking stack corresponding to a directory node.
     */
    static class DirectoryNode implements Cloneable {

        private final Path dir;
        private final boolean skip;

        private DirectoryStream<Path> stream = null;
        private Iterator<Path> iterator = null;

        /**
         *
         * @param dir
         * @param stream can be null to indicate the directory shall be skipped
         */
        DirectoryNode(final Path dir, final boolean skip) {
            this.dir = dir;
            this.skip = skip;
        }

        Path directory() {
            return dir;
        }

        Iterator<Path> iterator() throws IOException {
            if (iterator == null) {
                stream = Files.newDirectoryStream(dir);
                iterator = stream.iterator();
            }

            return iterator;
        }

        boolean skipped() {
            return skip;
        }

        void close() {
            try {
                if (stream != null) {
                    stream.close();
                    //
                    // if the path has been created by the walker to enter an
                    // archive, let's close its file system
                    //
                    if (dir instanceof Vfs2NioPath) {
                        dir.getFileSystem().close();
                    }
                }
            } catch (IOException | UnsupportedOperationException x) {
                //
                // nothing we can do here...
                //
            }
            iterator = null;
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
        try {
            Files.newDirectoryStream(entry).close();
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
            queue.add(new DirectoryNode(entry, true)); // do not  walk into this entry
        } else {
            queue.add(new DirectoryNode(entry, false)); // note that stream is null in case of error
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

            if (!top.skipped()) {
                IOException ioe = null;
                try {
                    Iterator<Path> iterator = top.iterator();
                    walkingResult = CONTINUE;
                    while (!stopWalking(walkingResult) && !skipSiblings(walkingResult) && iterator.hasNext()) {
                        try {
                            Path child = iterator.next();

                            walkingResult = visit(child, true);
                            if (keepWalking(walkingResult)) {
                                child = walkableFile(child);
                                if (child != null) {
                                    walkingResult = visit(child, true);
                                }
                            }
                        } catch (IOException x) {
                            //
                            // if an error is encountered itearating a folder, the
                            // exception is passed to postVisit()
                            //
                            ioe = x;
                        }
                    } // no more children
                } catch (IOException x) {
                    //
                    // Error in opening the folder
                    //
                    walkingResult = visitor.visitFileFailed(top.directory(), x);
                }

                if (!stopWalking(walkingResult)) {
                    walkingResult = visitor.postVisitDirectory(top.directory(), ioe);
                }
            }
            top.close(); queue.poll();
        }
    }

    /**
     * Pops the directory node that is the current top of the stack so that
     * there are no more events for the directory (including no END_DIRECTORY)
     * event. This method is a no-op if the stack is empty or the walker is
     * closed.
     */
    void pop() {
       queue.poll().close();
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

    private boolean hasChildren(Iterator i) {
        return ((i != null) && i.hasNext());
    }

    private Path walkableFile(Path entry) throws IOException {
        if (!walkIntoFiles) {
            return null;
        }

        Optional<URI> archiveUri = ArchiveDetector.uriIfArchive(entry);
        if (archiveUri.isPresent()) {
            Vfs2NioFileSystem fs = (Vfs2NioFileSystem)FileSystems.newFileSystem(
                archiveUri.get(), Collections.EMPTY_MAP
            );
            return new Vfs2NioPath(fs);
        }

        return null;
    }

    // --------------------------------------------------------- ArchiveDetector

    public static class ArchiveDetector {
        public final static List<String> SUPPORTED_ARCHIVES = Arrays.asList(new String[] {
            "tar", "tar.gz", "tgz", "zip", "jar", "gz", "bz2"
        });

        public static Optional<URI> uriIfArchive(Path path) {
            final String pathname = path.toString().toLowerCase();

            if (pathname.endsWith(".tar.gz")) {
                return Optional.of(fix(path.toUri(), "tgz"));
            }

            int pos = pathname.lastIndexOf('.');
            if ((pos < 0) || (pos == pathname.length()-1)) {
                return Optional.empty();
            }
            final String ext = pathname.substring(pos+1);
            return SUPPORTED_ARCHIVES.contains(ext) ? Optional.of(fix(path.toUri(), ext)) : Optional.empty();
        }

        private static URI fix(final URI uri, final String scheme) {
            final String uriString = uri.toString();

            return uriString.startsWith("vfs:") ?
                URI.create(uriString.replaceFirst("^vfs:", "vfs:" + scheme + ':')) :
                URI.create("vfs:" + scheme + ":" + uriString);
        }
    }
}
