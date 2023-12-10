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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.vfs2.FileObject;

import org.apache.nio.ImmutableList;

public class Vfs2NioPath implements Path {

    public final ImmutableList<String> names;
    private final Vfs2NioFileSystem fileSystem;

    public Vfs2NioPath(Vfs2NioFileSystem fileSystem, ImmutableList<String> names) {
        this.fileSystem = fileSystem;
        this.names = names;
    }

    public Vfs2NioPath(Vfs2NioFileSystem fileSystem, String... names) {
        this(fileSystem, new ImmutableList<>(names));
    }

    @Override
    public int compareTo(Path paramPath) {
        Vfs2NioPath p = checkPath(paramPath);
        int c = compare(getRoot().toString(), p.getRoot().toString());
        if (c != 0) {
            return c;
        }
        for (int i = 0; i < Math.min(names.size(), p.names.size()); i++) {
            String n1 = names.get(i);
            String n2 = p.names.get(i);
            c = compare(n1, n2);
            if (c != 0) {
                return c;
            }
        }
        return names.size() - p.names.size();
    }

    @Override
    public boolean endsWith(Path other) {
        Vfs2NioPath p = checkPath(other);
        if (p.isAbsolute()) {
            return compareTo(p) == 0;
        }
        return endsWith(names, p.names);
    }

    @Override
    public boolean endsWith(String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Path
                && compareTo((Path) obj) == 0;
    }

    @Override
    public Vfs2NioPath getFileName() {
        if (!names.isEmpty()) {
            return create(names.get(names.size() - 1));
        }
        return null;
    }

    @Override
    public Vfs2NioFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public Vfs2NioPath getName(int index) {
        int maxIndex = getNameCount();
        if ((index < 0) || (index >= maxIndex)) {
            throw new IllegalArgumentException("Invalid name index " + index + " - not in range [0-" + maxIndex + "]");
        }
        return create(names.subList(index, index + 1));
    }

    @Override
    public int getNameCount() {
        return names.size();
    }

    @Override
    public Vfs2NioPath getParent() {
        if (names.isEmpty() || ((names.size() == 1) && (getRoot() == null))) {
            return null;
        }
        return create(names.subList(0, names.size() - 1));
    }

    @Override
    public Path getRoot() {
        return fileSystem.getRoot();
    }

    @Override
    public int hashCode() {
        int hash = Objects.hashCode(getFileSystem());
        // use hash codes from toString() form of names
        hash = 31 * hash + Objects.hashCode(getRoot());
        for (String name : names) {
            hash = 31 * hash + Objects.hashCode(name);
        }
        return hash;
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public Iterator<Path> iterator() {
        return new AbstractList<Path>() {
            @Override
            public Path get(int index) {
                return getName(index);
            }

            @Override
            public int size() {
                return getNameCount();
            }
        }.iterator();
    }

    @Override
    public Vfs2NioPath normalize() {
        if (isNormal()) {
            return this;
        }

        Deque<String> newNames = new ArrayDeque<>();
        for (String name : names) {
            if (name.equals("..")) {
                String lastName = newNames.peekLast();
                if (lastName != null && !lastName.equals("..")) {
                    newNames.removeLast();
                } else if (!isAbsolute()) {
                    // if there's a root and we have an extra ".." that would go up above the root, ignore it
                    newNames.add(name);
                }
            } else if (!name.equals(".")) {
                newNames.add(name);
            }
        }

        return newNames.equals(names) ? this : create(newNames);
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        return register(watcher, events, (WatchEvent.Modifier[]) null);
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Register to watch " + toAbsolutePath() + " N/A");
    }

    @Override
    public Vfs2NioPath relativize(Path other) {
        Vfs2NioPath p = checkPath(other);
        if (!Objects.equals(getRoot(), p.getRoot())) {
            throw new IllegalArgumentException("Paths have different roots: " + this + ", " + other);
        }
        if (p.equals(this)) {
            return create();
        }
        if (getRoot() == null && names.isEmpty()) {
            return p;
        }
        // Common subsequence
        int sharedSubsequenceLength = 0;
        for (int i = 0; i < Math.min(names.size(), p.names.size()); i++) {
            if (names.get(i).equals(p.names.get(i))) {
                sharedSubsequenceLength++;
            } else {
                break;
            }
        }
        int extraNamesInThis = Math.max(0, names.size() - sharedSubsequenceLength);
        List<String> extraNamesInOther = (p.names.size() <= sharedSubsequenceLength)
                ? Collections.<String>emptyList()
                : p.names.subList(sharedSubsequenceLength, p.names.size());
        List<String> parts = new ArrayList<>(extraNamesInThis + extraNamesInOther.size());
        // add .. for each extra name in this path
        parts.addAll(Collections.nCopies(extraNamesInThis, ".."));
        // add each extra name in the other path
        parts.addAll(extraNamesInOther);
        return create(parts);
    }

    @Override
    public Vfs2NioPath resolve(Path other) {
        Vfs2NioPath p = checkPath(other);
        if (p.isAbsolute()) {
            return p;
        }
        if (p.names.isEmpty()) {
            return this;
        }
        String[] names = new String[this.names.size() + p.names.size()];
        int index = 0;
        for (String n : names) {
            names[index++] = n;
        }
        for (String n : p.names) {
            names[index++] = n;
        }
        return create(names);
    }

    @Override
    public Vfs2NioPath resolve(String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @Override
    public Path resolveSibling(Path other) {
        Vfs2NioPath parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public boolean startsWith(Path other) {
        Vfs2NioPath p = checkPath(other);
        return Objects.equals(getFileSystem(), p.getFileSystem())
                && Objects.equals(getRoot(), p.getRoot())
                && startsWith(names, p.names);
    }

    @Override
    public boolean startsWith(String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public Vfs2NioPath subpath(int beginIndex, int endIndex) {
        int maxIndex = getNameCount();
        if ((beginIndex < 0) || (beginIndex >= maxIndex) || (endIndex > maxIndex) || (beginIndex >= endIndex)) {
            throw new IllegalArgumentException("subpath(" + beginIndex + "," + endIndex + ") bad index range - allowed [0-" + maxIndex + "]");
        }
        return create(names.subList(beginIndex, endIndex));
    }

    @Override
    public Path toAbsolutePath() {
        //
        // An absolute path is complete in that it doesn't need to be combined
        // with other path information in order to locate a file. With Vfs2Nio
        // all Vfs2NioPaths have all information needed to locate a file using
        // their FileSystem
        //
        return this;
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("To file " + toAbsolutePath() + " N/A");
    }

    @Override
    public String toString() {
        return getPathname();
    }

    @Override
    public URI toUri() {
        return URI.create(String.format(
            "%s:%s%s",
            fileSystem.provider().getScheme(),
            fileSystem.root.getPublicURIString(),
            toString()
        ));
    }

    @SuppressWarnings("unchecked")
    protected Vfs2NioPath checkPath(Path paramPath) {
        if (paramPath.getClass() != getClass()) {
            throw new ProviderMismatchException("Path is not of this class: " + paramPath + "[" + paramPath.getClass().getSimpleName() + "]");
        }
        Vfs2NioPath t = (Vfs2NioPath) paramPath;

        FileSystem fs = t.getFileSystem();
        if (fs.provider() != this.fileSystem.provider()) {
            throw new ProviderMismatchException("Mismatched providers for " + t);
        }
        return t;
    }

    protected int compare(String s1, String s2) {
        if (s1 == null) {
            return s2 == null ? 0 : -1;
        } else {
            return s2 == null ? +1 : s1.compareTo(s2);
        }
    }

    protected Vfs2NioPath create(Collection<String> names) {
        return create(new ImmutableList<>(names.toArray(new String[names.size()])));
    }

    protected Vfs2NioPath create(ImmutableList<String> names) {
        return fileSystem.create(names);
    }

    protected Vfs2NioPath create(String... names) {
        return create(new ImmutableList<>(names));
    }

    protected boolean endsWith(List<?> list, List<?> other) {
        return other.size() <= list.size() && list.subList(list.size() - other.size(), list.size()).equals(other);
    }

    protected boolean isNormal() {
        int count = getNameCount();
        if ((count == 0) || ((count == 1) && !isAbsolute())) {
            return true;
        }
        boolean foundNonParentName = isAbsolute(); // if there's a root, the path doesn't start with ..
        boolean normal = true;
        for (String name : names) {
            if (name.equals("..")) {
                if (foundNonParentName) {
                    normal = false;
                    break;
                }
            } else {
                if (name.equals(".")) {
                    normal = false;
                    break;
                }
                foundNonParentName = true;
            }
        }
        return normal;
    }

    protected boolean startsWith(List<?> list, List<?> other) {
        return list.size() >= other.size() && list.subList(0, other.size()).equals(other);
    }

    private String getPathname() {
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String separator = getFileSystem().getSeparator();
        for (String name : names) {
            if ((sb.length() > 0) && (sb.charAt(sb.length() - 1) != '/')) {
                sb.append(separator);
            }
            sb.append(name);
        }

        return sb.toString();
    }

    public FileObject toFileObject() {
        return fileSystem.pathToFileObject(this);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // TODO: handle links
        var absolute = toAbsolutePath();
        var fs = getFileSystem();
        var provider = fs.provider();
        provider.checkAccess(absolute);
        return absolute;
    }

    boolean exists() {
        return getFileSystem().exists(normalize());
    }

    Vfs2NioFileAttributes getAttributes() throws IOException {
        var zfas = getFileSystem().getFileAttributes(normalize());
        if (zfas == null) {
            throw new NoSuchFileException(toString());
        }
        return zfas;
    }

    FileStore getFileStore() throws IOException {
        // each ZipFileSystem only has one root (as requested for now)
        if (exists()) {
            return fileSystem.getFileStore(this);
        }
        throw new NoSuchFileException(normalize().toString());
    }

    Map<String, Object> readAttributes(String attributes, LinkOption... options) throws IOException {
        String view = null;
        String attrs = null;
        int colonPos = attributes.indexOf(':');
        if (colonPos == -1) {
            view = "basic";
            attrs = attributes;
        } else {
            view = attributes.substring(0, colonPos++);
            attrs = attributes.substring(colonPos);
        }
        var zfv = Vfs2NioFileAttributeView.get(this, view);
        if (zfv == null) {
            throw new UnsupportedOperationException("view not supported");
        }
        return zfv.readAttributes(attrs);
    }

    void setAttribute(String attribute, Object value, LinkOption... options) throws IOException {
        String type = null;
        String attr = null;
        int colonPos = attribute.indexOf(':');
        if (colonPos == -1) {
            type = "basic";
            attr = attribute;
        } else {
            type = attribute.substring(0, colonPos++);
            attr = attribute.substring(colonPos);
        }
        var view = Vfs2NioFileAttributeView.get(this, type);
        if (view == null) {
            throw new UnsupportedOperationException("view <" + view + "> is not supported");
        }
        view.setAttribute(attr, value);
    }

    void setTimes(FileTime mtime, FileTime atime, FileTime ctime) throws IOException {
        getFileSystem().setTimes(normalize(), mtime, atime, ctime);
    }
}
