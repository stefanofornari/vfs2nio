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
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.nio.ImmutableList;
import static ste.vfs2nio.tools.Vfs2NioUtils.splitPathName;


public class Vfs2NioFileSystem extends FileSystem {

    private static final Set<String> supportedFileAttributeViews = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList("basic", "vfs")));
    private boolean open = true;

    private final Vfs2NioFileSystemProvider fileSystemProvider;

    protected FileObject root;
    protected Path rootPath;

    public Vfs2NioFileSystem(Vfs2NioFileSystemProvider fileSystemProvider, FileObject root) {
        this.fileSystemProvider = fileSystemProvider;
        this.root = root;
        this.rootPath = new Vfs2NioPath(this, "");  // "" means root
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException("No file stores available");
    }

    @Override
    public Vfs2NioPath getPath(String first, String... more) {
        if (first == null) {
            throw new IllegalArgumentException("first can not be null");
        }

        if (more == null) {
            throw new IllegalArgumentException("any more can not be null (element at index 0 is null)");
        } else {
            for (int i=0; i<more.length; ++i) {
                if (more[i] == null) {
                    throw new IllegalArgumentException(
                        String.format("any more can not be null (element at index %d is null)", i)
                    );
                }
            }
        }
        if (first.startsWith("!")) {
            first = first.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        if (first != null && first.length() > 0) {
            appendDedupSep(sb, first.replace('\\', '/'));   // in case we are running on Windows
        }

        if (more.length > 0) {
            for (String segment : more) {
                if ((sb.length() > 0) && (sb.charAt(sb.length() - 1) != '/')) {
                    sb.append('/');
                }
                // in case we are running on Windows
                appendDedupSep(sb, segment.replace('\\', '/'));
            }
        }

        if ((sb.length() > 1) && (sb.charAt(sb.length() - 1) == '/')) {
            sb.setLength(sb.length() - 1);
        }

        String path = sb.toString();

        return create(splitPathName(path));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        int colonIndex = syntaxAndPattern.indexOf(':');
        if ((colonIndex <= 0) || (colonIndex == syntaxAndPattern.length() - 1)) {
            throw new IllegalArgumentException("syntaxAndPattern must have form \"syntax:pattern\" but was \"" + syntaxAndPattern + "\"");
        }

        String syntax = syntaxAndPattern.substring(0, colonIndex);
        String pattern = syntaxAndPattern.substring(colonIndex + 1);
        String expr;
        switch (syntax) {
            case "glob":
                expr = globToRegex(pattern);
                break;
            case "regex":
                expr = pattern;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported path matcher syntax: \'" + syntax + "\'");
        }
        final Pattern regex = Pattern.compile(expr);
        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                Matcher m = regex.matcher(path.toString());
                return m.matches();
            }
        };
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.<Path>singleton(rootPath);
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("UserPrincipalLookupService is not supported.");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Watch service N/A");
    }

    @Override
    public Vfs2NioFileSystemProvider provider() {
        return fileSystemProvider;
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

    @Override
    public void close() throws IOException {
        provider().removeFileSystem(this);
        open = false;
    }

    public Path getRoot() {
        return rootPath;
    }

    public Path getDefaultDir() {
        return rootPath;
    }

    protected void appendDedupSep(StringBuilder sb, CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch != '/') || (sb.length() == 0) || (sb.charAt(sb.length() - 1) != '/')) {
                sb.append(ch);
            }
        }
    }

    protected Vfs2NioPath create(Collection<String> names) {
        return create(new ImmutableList<>(names.toArray(new String[names.size()])));
    }

    protected Vfs2NioPath create(String... names) {
        return create(new ImmutableList<>(names));
    }

    protected String globToRegex(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length());
        int inGroup = 0;
        int inClass = 0;
        int firstIndexInClass = -1;
        char[] arr = pattern.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            switch (ch) {
                case '\\':
                    if (++i >= arr.length) {
                        sb.append('\\');
                    } else {
                        char next = arr[i];
                        switch (next) {
                            case ',':
                                // escape not needed
                                break;
                            case 'Q':
                            case 'E':
                                // extra escape needed
                                sb.append("\\\\");
                                break;
                            default:
                                sb.append('\\');
                                break;
                        }
                        sb.append(next);
                    }
                    break;
                case '*':
                    sb.append(inClass == 0 ? ".*" : "*");
                    break;
                case '?':
                    sb.append(inClass == 0 ? '.' : '?');
                    break;
                case '[':
                    inClass++;
                    firstIndexInClass = i + 1;
                    sb.append('[');
                    break;
                case ']':
                    inClass--;
                    sb.append(']');
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    if (inClass == 0 || (firstIndexInClass == i && ch == '^')) {
                        sb.append('\\');
                    }
                    sb.append(ch);
                    break;
                case '!':
                    sb.append(firstIndexInClass == i ? '^' : '!');
                    break;
                case '{':
                    inGroup++;
                    sb.append('(');
                    break;
                case '}':
                    inGroup--;
                    sb.append(')');
                    break;
                case ',':
                    sb.append(inGroup > 0 ? '|' : ',');
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    public Vfs2NioFileAttributes getFileAttributes(Vfs2NioPath path) {
        return new Vfs2NioFileAttributes(pathToFileObject(path));
    }

    public FileObject getRootObject() {
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

    public FileObject pathToFileObject(Vfs2NioPath path) {
        try {
            return root.resolveFile(path.toString());
        } catch (FileSystemException e) {
            throw new Vfs2NioException("Failed to resolve.", e);
        }
    }

    public FileStore getFileStore(Vfs2NioPath path) {
        return new Vfs2NioFileStore(path);
    }

    protected Vfs2NioPath create(ImmutableList<String> names) {
        return new Vfs2NioPath(this, names);
    }

    boolean exists(Vfs2NioPath path) {
        try {
            return pathToFileObject(path).exists();
        } catch (Exception e) {
            return false;
        }
    }
}
