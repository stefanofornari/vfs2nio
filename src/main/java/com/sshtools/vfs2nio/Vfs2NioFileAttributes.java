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

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.vfs2.FileContentInfo;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;

public class Vfs2NioFileAttributes implements BasicFileAttributes {

    private final FileObject e;

    Vfs2NioFileAttributes(FileObject e) {
        this.e = e;
    }

    public Map<String, Object> attributes() {
        try {
            return e.getContent().getAttributes();
        } catch (FileSystemException e) {
        }
        return Collections.emptyMap();
    }

    public Certificate[] certificates() {
        try {
            return e.getContent().getCertificates();
        } catch (FileSystemException e) {
        }
        return null;
    }

    public String contentEncoding() {
        try {
            FileContentInfo info = e.getContent().getContentInfo();
            if (info != null) {
                return info.getContentEncoding();
            }
        } catch (FileSystemException e) {
        }
        return null;
    }

    public String contentType() {
        try {
            FileContentInfo info = e.getContent().getContentInfo();
            if (info != null) {
                return info.getContentType();
            }
        } catch (FileSystemException e) {
        }
        return null;
    }

    @Override
    public FileTime creationTime() {
        /*
		 * TODO - maybe from attributes, but those are specific to the file
		 * system
         */
        return null;
    }

    @Override
    public Object fileKey() {
        return e;
    }

    @Override
    public boolean isDirectory() {
        try {
            //
            // when VFS resolves a path whith a archive file system it
            // gives it type IMAGINERY
            //
            FileType t = e.getType();
            return ((t == FileType.FILE_OR_FOLDER) || (t == FileType.FOLDER) || (t == FileType.IMAGINARY));
        } catch (FileSystemException e) {
            return false;
        }
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public boolean isRegularFile() {
        try {
            //
            // when VFS resolves a path whith a archive file system it
            // gives it type IMAGINERY
            //
            FileType t = e.getType();
            return (t == FileType.FILE) || (t == FileType.IMAGINARY);
        } catch (FileSystemException e) {
            return false;
        }
    }

    @Override
    public boolean isSymbolicLink() {
        try {
            return e.isSymbolicLink();
        } catch (FileSystemException e) {
            throw new IllegalStateException("Could not determine if symbolic link.");
        }
    }

    @Override
    public FileTime lastAccessTime() {
        /*
		 * TODO - maybe from attributes, but those are specific to the file
		 * system
         */
        return null;
    }

    @Override
    public FileTime lastModifiedTime() {
        try {
            return FileTime.fromMillis(e.getContent().getLastModifiedTime());
        } catch (FileSystemException e) {
            return null;
        }
    }

    @Override
    public long size() {
        try {
            return e.getContent().getSize();
        } catch (FileSystemException e) {
            return 0;
        }
    }
}
