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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Walks a file tree, generating a sequence of events corresponding to the files
 * in the tree.
 *
 * This variant from the original class provides the option to walk inside
 * archives using the virtual file system associated with the archive. The
 * supported formats are tar, tgz, tar.gz, zip, jar, gz, bz2 so that when a file
 * whose extension matches one of the supported formats, two events are
 * generated: first an ENTRY event, then a DIRECTORY_OPEN event. The archive is
 * walked into until the last file, after which a DIRECTORY_END even is fired.
 *
 * All paths in the archive share the same file system, which is closed at the
 * end of the processing.
 *
 *
 *
 * {@snippet lang=java :
 *     Path top = ...
 *     int maxDepth = ...
 *     boolean followLinks = ...
 *
 *     try (FileSystemTreeWalker walker = new FileTreeWalker(followLinks, maxDepth)) {
 *         FileTreeWalker.Event ev = walker.walk(top);
 *         do {
 *             process(ev);
 *             ev = walker.next();
 *         } while (ev != null);
 *     }
 * }
 *
 */

public class FileSystemTreeWalker implements Closeable {
    public final int maxDepth;
    public final boolean followLinks;
    public final boolean walkInto;
    private boolean closed;
    private final LinkOption[] linkOptions;
    private final ArrayDeque<DirectoryNode> stack = new ArrayDeque<>();

    /**
     * The element on the walking stack corresponding to a directory node.
     */
    private static class DirectoryNode {
        private final Path dir;
        private final Object key;
        private final DirectoryStream<Path> stream;
        private final Iterator<Path> iterator;
        private boolean skipped;

        DirectoryNode(Path dir, Object key, DirectoryStream<Path> stream) {
            this.dir = dir;
            this.key = key;
            this.stream = stream;
            this.iterator = stream.iterator();
        }

        Path directory() {
            return dir;
        }

        Object key() {
            return key;
        }

        DirectoryStream<Path> stream() {
            return stream;
        }

        Iterator<Path> iterator() {
            return iterator;
        }

        void skip() {
            skipped = true;
        }

        boolean skipped() {
            return skipped;
        }
    }

    /**
     * The event types.
     */
    static enum EventType {
        /**
         * Start of a directory
         */
        START_DIRECTORY,
        /**
         * End of a directory
         */
        END_DIRECTORY,
        /**
         * An entry in a directory
         */
        ENTRY;
    }

    /**
     * Events returned by the {@link #walk} and {@link #next} methods.
     */
    static class Event {
        private final EventType type;
        private final Path file;
        private final boolean directory;
        private final IOException ioe;

        private Event(EventType type, Path file, boolean directory, IOException ioe) {
            this.type = type;
            this.file = file;
            this.directory = directory;
            this.ioe = ioe;
        }

        Event(EventType type, Path file) {
            this(type, file, false, null);
        }

        Event(EventType type, Path file, boolean directory) {
            this(type, file, directory, null);
        }

        Event(EventType type, Path file, IOException ioe) {
            this(type, file, false, ioe);
        }

        EventType type() {
            return type;
        }

        Path file() {
            return file;
        }

        IOException ioeException() {
            return ioe;
        }
    }

    /**
     * Creates a {@code FileTreeWalker}.
     *
     * @param maxDepth max depth level visited
     * @param followLinks follows synonym links if true, does not follow otherwise
     * @param walkInto walk into archive files if true, does not walk into otherwise
     *
     * @throws  IllegalArgumentException
     *          if {@code maxDepth} is negative
     * @throws  ClassCastException
     *          if {@code options} contains an element that is not a
     *          {@code FileVisitOption}
     * @throws  NullPointerException
     *          if {@code options} is {@code null} or the options
     *          array contains a {@code null} element
     */
    public FileSystemTreeWalker(int maxDepth, boolean followLinks, boolean walkInto) {
        if (maxDepth < 0)
            throw new IllegalArgumentException("maxDepth can not be negative");

        this.maxDepth = maxDepth;
        this.followLinks = followLinks;
        this.walkInto = walkInto;
        this.linkOptions = (followLinks) ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
        this.closed = false;
    }

    /**
     * Same as FileSystemTreeWalker(0, true);
     */
    public FileSystemTreeWalker() {
        this(0, true, true);
    }

    /**
     * Returns true if walking into the given directory would result in a
     * file system loop/cycle.
     */
    private boolean wouldLoop(Path dir) {
        // if this directory and ancestor has a file key then we compare
        // them; otherwise we use less efficient isSameFile test.

        String key = fileKey(dir);
        for (DirectoryNode ancestor: stack) {
            Object ancestorKey = ancestor.key();
            if (key != null && ancestorKey != null) {
                if (key.equals(ancestorKey)) {
                    // cycle detected
                    return true;
                }
            } else {
                try {
                    if (Files.isSameFile(dir, ancestor.directory())) {
                        // cycle detected
                        return true;
                    }
                } catch (IOException | SecurityException x) {
                    // ignore
                }
            }
        }
        return false;
    }

    /**
     * Visits the given file, returning the {@code Event} corresponding to that
     * visit.
     *
     * The {@code ignoreSecurityException} parameter determines whether
     * any SecurityException should be ignored or not. If a SecurityException
     * is thrown, and is ignored, then this method returns {@code null} to
     * mean that there is no event corresponding to a visit to the file.
     *
     * The {@code canUseCached} parameter determines whether cached attributes
     * for the file can be used or not.
     */
    private Event visit(Path entry, boolean ignoreSecurityException, boolean canUseCached) {
        // at maximum depth or file is not a directory
        int depth = stack.size();
        if (depth >= maxDepth || !Files.isDirectory(entry)) {
            return new Event(EventType.ENTRY, entry, true);
        }

        //
        // check for cycles when following links and report an error in case of
        // a loop
        if (followLinks && wouldLoop(entry)) {
            return new Event(EventType.ENTRY, entry,
                             new FileSystemLoopException(entry.toString()));
        }

        // file is a directory, attempt to open it
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(entry);
        } catch (IOException ioe) {
            return new Event(EventType.ENTRY, entry, ioe);
        } catch (SecurityException se) {
            if (ignoreSecurityException)
                return null;
            throw se;
        }

        // push a directory node to the stack and return an event
        stack.push(new DirectoryNode(entry, fileKey(entry), stream));
        return new Event(EventType.START_DIRECTORY, entry, true);
    }


    /**
     * Start walking from the given file.
     */
    Event walk(Path file) {
        if (closed)
            throw new IllegalStateException("Closed");

        Event ev = visit(file,
                         false,   // ignoreSecurityException
                         false);  // canUseCached
        assert ev != null;
        return ev;
    }

    /**
     * Returns the next Event or {@code null} if there are no more events or
     * the walker is closed.
     */
    Event next() {
        DirectoryNode top = stack.peek();
        if (top == null)
            return null;      // stack is empty, we are done

        // continue iteration of the directory at the top of the stack
        Event ev;
        do {
            Path entry = null;
            IOException ioe = null;

            // get next entry in the directory
            if (!top.skipped()) {
                Iterator<Path> iterator = top.iterator();
                try {
                    if (iterator.hasNext()) {
                        entry = iterator.next();
                    }
                } catch (DirectoryIteratorException x) {
                    ioe = x.getCause();
                }
            }

            // no next entry so close and pop directory,
            // creating corresponding event
            if (entry == null) {
                try {
                    top.stream().close();
                } catch (IOException e) {
                    if (ioe == null) {
                        ioe = e;
                    } else {
                        ioe.addSuppressed(e);
                    }
                }
                stack.pop();
                return new Event(EventType.END_DIRECTORY, top.directory(), ioe);
            }

            // visit the entry
            ev = visit(entry,
                       true,   // ignoreSecurityException
                       true);  // canUseCached

        } while (ev == null);

        return ev;
    }

    /**
     * Pops the directory node that is the current top of the stack so that
     * there are no more events for the directory (including no END_DIRECTORY)
     * event. This method is a no-op if the stack is empty or the walker is
     * closed.
     */
    void pop() {
        if (!stack.isEmpty()) {
            DirectoryNode node = stack.pop();
            try {
                node.stream().close();
            } catch (IOException ignore) { }
        }
    }

    /**
     * Skips the remaining entries in the directory at the top of the stack.
     * This method is a no-op if the stack is empty or the walker is closed.
     */
    void skipRemainingSiblings() {
        if (!stack.isEmpty()) {
            stack.peek().skip();
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
            while (!stack.isEmpty()) {
                pop();
            }
            closed = true;
        }
    }

    /**
     * TODO: create a unique key to be able to detect cycling links in paths
     *
     * @param path the path to extract the key from
     *
     * @return the unique key
     */
    private String fileKey(Path path) {
        return path.toAbsolutePath().toString();
    }
}
