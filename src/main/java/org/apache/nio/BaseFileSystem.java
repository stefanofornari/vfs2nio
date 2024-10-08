/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.nio;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.vfs2.FileObject;
import static ste.vfs2nio.tools.Vfs2NioUtils.splitPathName;

public abstract class BaseFileSystem<T extends Path, P extends FileSystemProvider> extends FileSystem {

    private final P fileSystemProvider;

    protected FileObject root;

    public BaseFileSystem(P fileSystemProvider, FileObject root) {
        this.fileSystemProvider = fileSystemProvider;
        this.root = root;
    }

    public T getDefaultDir() {
        return getPath("/");
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException("No file stores available");
    }

    @Override
    public T getPath(String first, String... more) {
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
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

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
        return Collections.<Path>singleton(create("/"));
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
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Watch service N/A");
    }

    @Override
    public P provider() {
        return fileSystemProvider;
    }

    protected void appendDedupSep(StringBuilder sb, CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch != '/') || (sb.length() == 0) || (sb.charAt(sb.length() - 1) != '/')) {
                sb.append(ch);
            }
        }
    }

    protected T create(Collection<String> names) {
        return create(new ImmutableList<>(names.toArray(new String[names.size()])));
    }

    protected abstract T create(ImmutableList<String> names);

    protected T create(String... names) {
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

    public T getRoot() {
        return getPath("/");
    }
}
