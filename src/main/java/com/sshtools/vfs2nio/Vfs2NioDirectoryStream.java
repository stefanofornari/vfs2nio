/*
 * Copyright © 2018 - 2022 SSHTOOLS Limited (support@sshtools.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sshtools.vfs2nio;

import java.io.IOException;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 *  TODO: this iterator collects all children when created, which can be
 *  very resource consuming for big lists on remote file systems; we
 *  should make it lazy
 *
 */
public class Vfs2NioDirectoryStream implements DirectoryStream<Path> {

    private final DirectoryStream.Filter<? super Path> filter;
    private volatile Iterator<Path> iterator;
    private volatile boolean open = true;
    private final Vfs2NioPath path;

    Vfs2NioDirectoryStream(Vfs2NioPath path, DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new NotDirectoryException(path.toString());
        }
        this.path = path.normalize();
        this.filter = filter;
    }

    @Override
    public synchronized void close() throws IOException {
        open = false;
    }

    @Override
    public synchronized Iterator<Path> iterator() {
        if (!open) {
            throw new ClosedDirectoryStreamException();
        }
        if (iterator != null) {
            throw new IllegalStateException("iterator already created");
        }

        //
        // If we are here path.isDirectory() returned true(). If path.isRegularFile()
        // returns true as well, it represents a "mounted" file like an archive
        // or image. In such a case, we need to list the files inside the
        // mount point (i.e. the path root).
        //
        Vfs2NioFileSystem fs = path.getFileSystem();
        var obj = fs.getFileAttributes(path).isRegularFile() ? fs.root : fs.pathToFileObject(path);

        try {
            var children = obj.getChildren();
            return new Iterator<Path>() {
                int index;

                @Override
                public boolean hasNext() {
                    if (!open) {
                        return false;
                    }
                    return index < children.length;
                }

                @Override
                public synchronized Path next() {
                    if (!open) {
                        throw new NoSuchElementException();
                    }
                    //
                    // Create Path relative to their root
                    //
                    return new Vfs2NioPath(fs, children[index++].getName().getPath().substring(1));
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } catch (IOException x) {
            throw new IllegalStateException(x);
        }
    }
}
