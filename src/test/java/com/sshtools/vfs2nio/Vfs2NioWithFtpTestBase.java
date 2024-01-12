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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

/**
 *
 */
public class Vfs2NioWithFtpTestBase extends Vfs2NioTestBase {
    public static final String HOME_DIR = "/public";
    public static final String FILE = HOME_DIR + "/dir/sample.txt";
    public static final String CONTENT = "abcdef 1234567890";

    protected static FakeFtpServer FTP;

    @BeforeClass
    public static void before_all() throws Exception {
        FTP = new FakeFtpServer();
        FTP.setServerControlPort(0);  // use any free port

        org.mockftpserver.fake.filesystem.FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry(HOME_DIR));
        fileSystem.add(new DirectoryEntry(HOME_DIR+"/dir"));
        fileSystem.add(new FileEntry(FILE, CONTENT));
        FTP.setFileSystem(fileSystem);

        UserAccount userAccount = new UserAccount("anonymous", "anonymous", HOME_DIR);
        FTP.addUserAccount(userAccount);

        FTP.start();
    }

    @AfterClass
    public static void after_all() throws Exception {
        FTP.stop();
    }

}
