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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.UserAuthenticationData;
import org.apache.commons.vfs2.UserAuthenticationData.Type;
import org.apache.commons.vfs2.UserAuthenticator;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.util.RandomAccessMode;

public class Vfs2NioFileSystemProvider extends FileSystemProvider {

    public final static String FILE_SYSTEM_OPTIONS = "com.sshtools.vfs2nio.fileSystemOptions";
    public final static String VFS_MANAGER = "com.sshtools.vfs2nio.vfsManager";
    public final static String AUTHENTICATOR = "com.sshtools.vfs2nio.vfsAuthenticator";
    public final static String USERNAME = "com.sshtools.vfs2nio.username";
    public final static String PASSWORD = "com.sshtools.vfs2nio.password";
    public final static String DOMAIN = "com.sshtools.vfs2nio.domain";

    // Checks that the given file is a UnixPath
    static final Vfs2NioPath toVFSPath(Path path) {
        if (path == null) {
            throw new NullPointerException();
        }
        if (!(path instanceof Vfs2NioPath)) {
            throw new ProviderMismatchException();
        }
        return (Vfs2NioPath) path;
    }

    protected static final Map<FileObject, Vfs2NioFileSystem> filesystems = Collections.synchronizedMap(new HashMap<>());

    protected static final long TRANSFER_SIZE = 8192;

    public Vfs2NioFileSystemProvider() {
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        var p = toVFSPath(path);
        var fo = p.toFileObject();
        for (AccessMode m : modes) {
            switch (m) {
                case EXECUTE:
                    break;
                case READ:
                    if (!fo.isReadable()) {
                        throw new AccessDeniedException(String.format("No %s access to %s", m, path));
                    }
                    break;
                case WRITE:
                    if (!fo.isWriteable()) {
                        throw new AccessDeniedException(String.format("No %s access to %s", m, path));
                    }
                    break;
                default:
                    break;
            }
        }
        if (modes.length == 0 && !fo.exists()) {
            throw new NoSuchFileException(path.toString());
        }
    }

    @Override
    public void copy(Path src, Path target, CopyOption... options) throws IOException {
        /*
         * TODO: Support REPLACE_EXISTING, COPY_ATTRIBUTES, ATOMIC_MOVE if possible
         */
        toVFSPath(target).toFileObject().copyFrom(toVFSPath(src).toFileObject(), new AllFileSelector());
    }

    @Override
    public void createDirectory(Path path, FileAttribute<?>... attrs) throws IOException {
        /* TODO: Support attributes */
        var p = toVFSPath(path);
        checkAccess(p, AccessMode.WRITE);
        var fo = p.toFileObject();
        if (fo.exists()) {
            throw new FileAlreadyExistsException(p.toString());
        }
        fo.createFolder();
    }

    @Override
    public final void delete(Path path) throws IOException {
        var p = toVFSPath(path);
        checkAccess(p, AccessMode.WRITE);
        var fo = p.toFileObject();
        fo.deleteAll();
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return Vfs2NioFileAttributeView.get(toVFSPath(path), type);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return toVFSPath(path).getFileStore();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        try {
            synchronized (filesystems) {
                Vfs2NioFileSystem vfs = null;

                var path = toFsUri(uri);

                vfs = filesystems.get(
                    VFS.getManager().resolveFile(path).getFileSystem().getRoot()
                );
                if (vfs == null) {
                    throw new FileSystemNotFoundException(String.format("Cannot find file system for %s", uri));
                }
                return vfs;
            }
        } catch (IOException x) {
            throw new FileSystemNotFoundException(x.getMessage());
        }
    }

    @Override
    public Path getPath(URI uri) {
        Vfs2NioFileSystem fileSystem;
        try {
            fileSystem = (Vfs2NioFileSystem) getFileSystem(uri);
        } catch (FileSystemNotFoundException fsnfe) {
            try {
                fileSystem = (Vfs2NioFileSystem) newFileSystem(uri, new HashMap<>());
            } catch (IOException e) {
                throw new Vfs2NioException("Failed to create new file system.", e);
            }
        }

        //
        // Create a relative path from the given path and the determined root
        //
        String root = fileSystem.root.getPublicURIString();
        String rootPath = root.substring(root.indexOf(":///")+4);
        if (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length()-1);
        }
        if (rootPath.endsWith("!")) {
            rootPath = rootPath.substring(0, rootPath.length()-1);
        }
        String uriPath = uri.toString().substring(uri.toString().indexOf(":///")+4);

        return fileSystem.getPath(uriPath.replaceFirst("^" + rootPath, ""));
    }

    @Override
    public String getScheme() {
        return "vfs";
    }

    @Override
    public boolean isHidden(Path path) {
        try {
            return toVFSPath(path).toFileObject().isHidden();
        } catch (FileSystemException e) {
            return false;
        }
    }

    @Override
    public boolean isSameFile(Path path, Path other) throws IOException {
        return toVFSPath(path).toFileObject().equals(toVFSPath(other).toFileObject());
    }

    @Override
    public void move(Path src, Path target, CopyOption... options) throws IOException {
        toVFSPath(src).toFileObject().moveTo(toVFSPath(target).toFileObject());
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options,
            ExecutorService exec, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        return newFileChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path path, Filter<? super Path> filter) throws IOException {
        return new Vfs2NioDirectoryStream(toVFSPath(path), filter);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        /* TODO support more options */
        var fileObject = toVFSPath(path).toFileObject();
        if (fileObject.exists() && options.contains(StandardOpenOption.CREATE_NEW)) {
            throw new FileAlreadyExistsException(path.toString());
        } else if (!fileObject.exists()
                && (options.contains(StandardOpenOption.CREATE_NEW) || options.contains(StandardOpenOption.CREATE))) {
            fileObject.createFile();
        }
        var content = fileObject.getContent();
        var rac = content.getRandomAccessContent(toRandomAccessMode(options));
        return new FileChannel() {

            @Override
            public int read(ByteBuffer dst) throws IOException {
                var arr = new byte[dst.remaining()];
                int r = rac.getInputStream().read(arr, 0, arr.length);
                if (r > 0) {
                    dst.put(arr, 0, r);
                }
                return r;
            }

            @Override
            public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
                long t = 0;
                for (var dst : dsts) {
                    var arr = new byte[dst.remaining()];
                    int r = rac.getInputStream().read(arr, offset, length);
                    if (r > 0) {
                        dst.put(arr, 0, r);
                    }
                    t += r;
                }
                return t;
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                var arr = new byte[src.remaining()];
                src.get(arr);
                rac.write(arr);
                return arr.length;
            }

            @Override
            public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
                long t = 0;
                for (var src : srcs) {
                    var arr = new byte[src.remaining()];
                    src.get(arr);
                    rac.write(arr, offset, length);
                    t += arr.length;
                }
                return t;
            }

            @Override
            public long position() throws IOException {
                return rac.getFilePointer();
            }

            @Override
            public FileChannel position(long newPosition) throws IOException {
                rac.seek(newPosition);
                return this;
            }

            @Override
            public long size() throws IOException {
                return rac.length();
            }

            @Override
            public FileChannel truncate(long size) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void force(boolean metaData) throws IOException {
                // Noop?
            }

            @Override
            public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
                // Untrusted target: Use a newly-erased buffer
                int c = (int) Math.min(count, TRANSFER_SIZE);
                ByteBuffer bb = ByteBuffer.allocate(c);
                long tw = 0; // Total bytes written
                long pos = position;
                try {
                    while (tw < count) {
                        bb.limit((int) Math.min(count - tw, TRANSFER_SIZE));
                        int nr = read(bb, pos);
                        if (nr <= 0) {
                            break;
                        }
                        bb.flip();
                        // ## Bug: Will block writing target if this channel
                        // ## is asynchronously closed
                        int nw = target.write(bb);
                        tw += nw;
                        if (nw != nr) {
                            break;
                        }
                        pos += nw;
                        bb.clear();
                    }
                    return tw;
                } catch (IOException x) {
                    if (tw > 0) {
                        return tw;
                    }
                    throw x;
                }
            }

            @Override
            public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
                // Untrusted target: Use a newly-erased buffer
                int c = (int) Math.min(count, TRANSFER_SIZE);
                ByteBuffer bb = ByteBuffer.allocate(c);
                long tw = 0; // Total bytes written
                long pos = position;
                try {
                    while (tw < count) {
                        bb.limit((int) Math.min((count - tw), (long) TRANSFER_SIZE));
                        // ## Bug: Will block reading src if this channel
                        // ## is asynchronously closed
                        int nr = src.read(bb);
                        if (nr <= 0) {
                            break;
                        }
                        bb.flip();
                        int nw = write(bb, pos);
                        tw += nw;
                        if (nw != nr) {
                            break;
                        }
                        pos += nw;
                        bb.clear();
                    }
                    return tw;
                } catch (IOException x) {
                    if (tw > 0) {
                        return tw;
                    }
                    throw x;
                }
            }

            @Override
            public int read(ByteBuffer dst, long position) throws IOException {
                position(position);
                return read(dst);
            }

            @Override
            public int write(ByteBuffer src, long position) throws IOException {
                position(position);
                return write(src);
            }

            @Override
            public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public FileLock lock(long position, long size, boolean shared) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public FileLock tryLock(long position, long size, boolean shared) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void implCloseChannel() throws IOException {
                rac.close();
            }

        };
    }

    // --------------------------------------------------------- private methods

    private RandomAccessMode toRandomAccessMode(Set<? extends OpenOption> options) {
        if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.CREATE)
                || options.contains(StandardOpenOption.CREATE_NEW)) {
            return RandomAccessMode.READWRITE;
        }
        return RandomAccessMode.READ;
    }

    @Override
    public Vfs2NioFileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
        return newFileSystem(path.toUri(), env);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Vfs2NioFileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        var path = toFsUri(uri);

        synchronized (filesystems) {
            FileSystemManager mgr = VFS.getManager();
            var opts = (env == null) ? null : (FileSystemOptions) env.get(FILE_SYSTEM_OPTIONS);
            if (opts == null) {
                opts = new FileSystemOptions();
            }
            if (!Arrays.asList(mgr.getSchemes()).contains(path.getScheme())) {
                /*
                 * TODO monitor state of Commons VFS JPMS compatibility for adjustments to
                 * message
                 */
                throw new IOException(String.format(
                        "The scheme %s is not available. Do you have all required commons-vfs libraries, as well as libraries the specific scheme needs? If you are using Java modules, you may need to open additional packages. As of today, 2022-06-11, Commons VFS is not JPMS aware. Support schemes are %s",
                        path.getScheme(), String.join(", ", mgr.getSchemes())));
            }
            var auth = (UserAuthenticator) env.get(AUTHENTICATOR);
            if (auth != null) {
                DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(opts, auth);
            } else {
                DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(opts,
                        new UA(uri, (Map<String, Object>) env));
            }

            /*
             * First resolve the URI without a path. For Commons VFS, this may either be the
             * actual root of the file system, or it might be some intermediate path such as
             * the user's home directory.
             *
             * Once the root is resolved, we then resolve the path against that, ALWAYS
             * treating it as an absolute path.
             *
             * Due to how Commons VFS works, we cannot support relative paths from the user
             * homes directory.
             */
            FileObject root = null;

            try {
                root = mgr.resolveFile(path).getFileSystem().getRoot();
            } catch (FileSystemException x) {
                root = mgr.resolveFile(path.toString(), opts);
            }

            if (filesystems.containsKey(root)) {
                throw new FileSystemAlreadyExistsException("A file system for " + root + " has been already created; use getFileSystem()");
            }

            var vfs = new Vfs2NioFileSystem(this, root, path);

            filesystems.put(root, vfs);
            return vfs;
        }
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        var optlist = Arrays.asList(options);
        if (optlist.contains(StandardOpenOption.WRITE)) {
            throw new IllegalArgumentException(
                    String.format("%s is not supported by this method.", StandardOpenOption.WRITE));
        }
        checkAccess(path, AccessMode.READ);
        return toVFSPath(path).toFileObject().getContent().getInputStream();
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        var optlist = Arrays.asList(options);
        if (optlist.contains(StandardOpenOption.READ)) {
            throw new IllegalArgumentException(
                    String.format("%s is not supported by this method.", StandardOpenOption.READ));
        }
        var fo = toVFSPath(path).toFileObject();
        if (optlist.contains(StandardOpenOption.CREATE_NEW) && fo.exists()) {
            throw new IOException(String.format("%s already exists, and the option %s was specified.", fo,
                    StandardOpenOption.CREATE_NEW));
        }
        checkAccess(path, AccessMode.WRITE);
        return fo.getContent().getOutputStream(optlist.contains(StandardOpenOption.APPEND));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        if (type == BasicFileAttributes.class || type == Vfs2NioFileAttributes.class) {
            return (A) toVFSPath(path).getAttributes();
        }
        return null;
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attribute, LinkOption... options) throws IOException {
        return toVFSPath(path).readAttributes(attribute, options);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        toVFSPath(path).setAttribute(attribute, value, options);
    }

    protected URI toPathlessURI(URI uri) {
        String root = null, plainUri = uri.toString();

        root = (plainUri.endsWith("tar")) ? uri.getPath() : File.separator;
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), root, uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    protected URI toFsUri(URI uri) {
        var scheme = uri.getScheme();
        if ((scheme == null) || !scheme.equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException(String.format("URI scheme must be %s", getScheme()));
        }
        try {
            var spec = uri.getRawSchemeSpecificPart();
            int sep = spec.indexOf("!/");
            if (sep != -1) {
                spec = spec.substring(0, sep);
            }
            var u = new URI(spec);
            return u;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    void removeFileSystem(Vfs2NioFileSystem fs) throws IOException {
        filesystems.remove(fs.root);
    }

    static class UA implements UserAuthenticator {

        private Map<String, Object> env;
        private URI uri;

        UA(URI uri, Map<String, Object> env) {
            this.uri = uri;
            this.env = env;
        }

        @Override
        public UserAuthenticationData requestAuthentication(Type[] types) {
            var typeList = Arrays.asList(types);
            var username = env == null ? System.getProperty("user.name")
                    : (String) env.getOrDefault(USERNAME, System.getProperty("user.name"));
            var domain = env == null ? null : (String) env.get(DOMAIN);
            char[] password = null;
            var pwobj = env == null ? null : env.get(PASSWORD);
            if (pwobj instanceof String) {
                password = ((String) pwobj).toCharArray();
            } else if (pwobj instanceof char[]) {
                password = ((char[]) pwobj);
            } else if (pwobj != null) {
                password = pwobj.toString().toCharArray();
            }

            if (uri.getUserInfo() != null) {
                username = uri.getUserInfo();
                int idx = username.indexOf('\\');
                if (idx != -1) {
                    domain = username.substring(0, idx);
                    username = username.substring(idx + 1);
                } else {
                    idx = username.indexOf('@');
                    if (idx != -1) {
                        domain = username.substring(idx + 1);
                        username = username.substring(0, idx);
                    }
                }
            }
            var ud = new UserAuthenticationData();
            var console = System.console();
            if (typeList.contains(UserAuthenticationData.DOMAIN)) {
                if (domain != null) {
                    ud.setData(UserAuthenticationData.DOMAIN, domain.toCharArray());
                }
            }
            if (typeList.contains(UserAuthenticationData.USERNAME)) {
                if (username == null) {
                    if (console == null) {
                        throw new IllegalStateException(
                                "A username is required, but a username has not been specified for this file system, and no way of obtaining interactively is available.");
                    } else {
                        username = console.readLine("Username: ");
                        if (username != null) {
                            int idx = username.indexOf('\\');
                            if (idx != -1) {
                                domain = username.substring(0, idx);
                                username = username.substring(idx + 1);
                                ud.setData(UserAuthenticationData.DOMAIN, domain.toCharArray());
                            } else {
                                idx = username.indexOf('@');
                                if (idx != -1) {
                                    domain = username.substring(idx + 1);
                                    username = username.substring(0, idx);
                                    ud.setData(UserAuthenticationData.DOMAIN, domain.toCharArray());
                                }
                            }
                        } else {
                            throw new IllegalArgumentException("No username. Aborted.");
                        }
                    }
                }
                ud.setData(UserAuthenticationData.USERNAME, username.toCharArray());
            }

            if (typeList.contains(UserAuthenticationData.PASSWORD)) {
                if (password == null) {
                    if (console == null) {
                        throw new IllegalStateException(
                                "A password is required, but a password has not been specified for this file system, and no way of obtaining interactively is available.");
                    } else {
                        password = console.readPassword("Password: ");
                    }
                }
                ud.setData(UserAuthenticationData.PASSWORD, password);
            }

            return ud;
        }
    }

}
