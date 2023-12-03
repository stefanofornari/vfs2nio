/*
 * Vs2Nio
 * ^^^^^^
 *
 * Modifications to the original Copyright (C) 2023 Stefano Fornari.
 * Licensed under the GUPL-1.2 or later (see LICENSE)
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
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.nio.BaseFileSystem;
import org.apache.nio.ImmutableList;

public class Vfs2NioFileSystem extends BaseFileSystem<Vfs2NioPath, Vfs2NioFileSystemProvider> {

    private static final Set<String> supportedFileAttributeViews = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList("basic", "vfs")));
    private boolean open = true;
    private FileObject root;

    private URI uri;

    public Vfs2NioFileSystem(Vfs2NioFileSystemProvider provider, FileObject root, URI uri) throws FileSystemException {
        super(provider);
        this.root = root;
        this.uri = uri;
    }

    @Override
    public void close() throws IOException {
        if (!open) {
            throw new IOException("Not open");
        }
        open = false;
        provider().removeFileSystem(uri);
    }

    public Vfs2NioFileAttributes getFileAttributes(Vfs2NioPath path) {
        return new Vfs2NioFileAttributes(pathToFileObject(path));
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        if (uri.getPath() == null) {
            return super.getRootDirectories();
        } else {
            var uriPath = uri.getPath();
            var fsRoot = getPath(uriPath);
            return Collections.<Path>singleton(fsRoot);
        }
    }

    public FileObject getRoot() {
        return root;
    }

    public long getTotalSpace() {
        // TODO from FileSystem attributes?
        return 0;
    }

    public long getUnallocatedSpace() {
        // TODO from FileSystem attributes?
        return 0;
    }

    public long getUsableSpace() {
        // TODO from FileSystem attributes?
        return 0;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isReadOnly() {
        try {
            return !root.isWriteable();
        } catch (FileSystemException e) {
            return true;
        }
    }

    //
    // TODO: this iterator collects all children when created, which can be
    // very resource consuming for big lists on remote file systems; we
    // should make it lazy
    public Iterator<Path> iterator(Path path, Filter<? super Path> filter) throws IOException {
        //
        // If we are here path.isDirectory() returned true(). If path.isRegularFile()
        // returns true as well, it represents a "mounted" file like an archive
        // or image. In such a case, we need to list the files inside the
        // mount point (i.e. the path root).
        //
        var obj = (Files.isRegularFile(path)) ? root : pathToFileObject(Vfs2NioFileSystemProvider.toVFSPath(path));
        var children = obj.getChildren(); // HERE
        return new Iterator<Path>() {
            int index;

            @Override
            public boolean hasNext() {
                return index < children.length;
            }

            @Override
            public Path next() {
//				var croot = path.getRoot();
//				var f = path.getFileName();
//				return new Vfs2NioPath(Vfs2NioFileSystem.this, croot.toString(),
//						(f == null ? "" : f.toString() + "/") + children[index++].getName().getBaseName().toString());

                return new Vfs2NioPath(Vfs2NioFileSystem.this, null, children[index++].getName().getPath());
            }
        };
    }

    public void setTimes(Vfs2NioPath path, FileTime mtime, FileTime atime, FileTime ctime) {
        if (atime != null || ctime != null) {
            throw new UnsupportedOperationException();
        }
        var object = pathToFileObject(path);
        try {
            object.getContent().setLastModifiedTime(mtime.toMillis());
        } catch (FileSystemException e) {
            throw new Vfs2NioException("Failed to set last modified.", e);
        }
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return supportedFileAttributeViews;
    }

    @Override
    protected Vfs2NioPath create(String root, ImmutableList<String> names) {
        return new Vfs2NioPath(this, root, names);
    }

    boolean exists(Vfs2NioPath path) {
        try {
            return pathToFileObject(path).exists();
        } catch (Exception e) {
            return false;
        }
    }

    FileStore getFileStore(Vfs2NioPath path) {
        return new Vfs2NioFileStore(path);
    }

    FileObject pathToFileObject(Vfs2NioPath path) {
        try {
            return root.resolveFile(path.toString());
        } catch (FileSystemException e) {
            throw new Vfs2NioException("Failed to resolve.", e);
        }
    }
}
