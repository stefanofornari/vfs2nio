package ste.vfs2nio.tools;

/*
 * Vs2Nio
 * ^^^^^^
 *
 * Copyright (C) 2024 Stefano Fornari. Licensed under the
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
import java.net.URI;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import org.junit.Test;

/**
 *
 */
public class Vfs2NioUtilsTest {

    @Test
    public void decodePath_preserves_separators() {
        then(Vfs2NioUtils.decodePath("with%20space/")).isEqualTo("with space/");
        then(Vfs2NioUtils.decodePath("/with%20space/")).isEqualTo("/with space/");
        then(Vfs2NioUtils.decodePath("/")).isEqualTo("/");
    }

    @Test
    public void splitFileName_returns_all_elements() {
        thenThrownBy(() -> Vfs2NioUtils.splitPathName(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pathname can not be null");

        then(Vfs2NioUtils.splitPathName("")).isEmpty();
        then(Vfs2NioUtils.splitPathName("one")).containsExactly("one");
        then(Vfs2NioUtils.splitPathName("one/two/")).containsExactly("one", "two", "");
        then(Vfs2NioUtils.splitPathName("one/two/")).containsExactly("one", "two", "");
        then(Vfs2NioUtils.splitPathName("/")).containsExactly("");
        then(Vfs2NioUtils.splitPathName("/one")).containsExactly("", "one");
        then(Vfs2NioUtils.splitPathName("/one/two")).containsExactly("", "one", "two");
        then(Vfs2NioUtils.splitPathName("/one/two/")).containsExactly("", "one", "two", "");
    }

    @Test
    public void extractUriElements_returns_uri_elements() throws Exception {
        thenThrownBy(() -> Vfs2NioUtils.extractUriElements(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("uri can not be null");

        UriElements elems = Vfs2NioUtils.extractUriElements(new URI("file:///"));
        then(elems.scheme()).isEqualTo("file");
        then(elems.authority()).isNull(); then(elems.path()).isEqualTo("/");
        then(elems.query()).isNull(); then(elems.fragment()).isNull();

        elems = Vfs2NioUtils.extractUriElements(new URI("vfs:tar:file:///"));
        then(elems.scheme()).isEqualTo("vfs:tar:file");
        then(elems.authority()).isNull(); then(elems.path()).isEqualTo("/");
        then(elems.query()).isNull(); then(elems.fragment()).isNull();

        elems = Vfs2NioUtils.extractUriElements(URI.create("vfs:ftp://user:password@somewhere.com:1234/some/path/afile1.txt?q1=p1&q2=p2#fragment1"));
        then(elems.scheme()).isEqualTo("vfs:ftp");
        then(elems.authority()).isEqualTo("user:password@somewhere.com:1234");
        then(elems.path()).isEqualTo("/some/path/afile1.txt");
        then(elems.query()).isEqualTo("q1=p1&q2=p2");
        then(elems.fragment()).isEqualTo("fragment1");

        elems = Vfs2NioUtils.extractUriElements(URI.create("scheme:///?"));
        then(elems.scheme()).isEqualTo("scheme");
        then(elems.authority()).isNull(); then(elems.path()).isEqualTo("/");
        then(elems.query()).isEqualTo(""); then(elems.fragment()).isNull();

        elems = Vfs2NioUtils.extractUriElements(URI.create("scheme://auth/path?"));
        then(elems.scheme()).isEqualTo("scheme");
        then(elems.authority()).isEqualTo("auth"); then(elems.path()).isEqualTo("/path");
        then(elems.query()).isEqualTo(""); then(elems.fragment()).isNull();

        elems = Vfs2NioUtils.extractUriElements(URI.create("scheme://auth/path#fragment2"));
        then(elems.scheme()).isEqualTo("scheme");
        then(elems.authority()).isEqualTo("auth"); then(elems.path()).isEqualTo("/path");
        then(elems.query()).isNull(); then(elems.fragment()).isEqualTo("fragment2");

        elems = Vfs2NioUtils.extractUriElements(URI.create("scheme:///path?p=1#"));
        then(elems.scheme()).isEqualTo("scheme");
        then(elems.authority()).isNull(); then(elems.path()).isEqualTo("/path");
        then(elems.query()).isEqualTo("p=1"); then(elems.fragment()).isEqualTo("");

        elems = Vfs2NioUtils.extractUriElements(URI.create("vfs:file:///some%20path/with%20and%3F.txt"));
        then(elems.scheme()).isEqualTo("vfs:file");
        then(elems.authority()).isNull();
        then(elems.path()).isEqualTo("/some path/with and?.txt");
        then(elems.query()).isNull(); then(elems.fragment()).isNull();
    }

    @Test
    public void fixEscapes_fixes_missing_escapes() {
        then(Vfs2NioUtils.fixUriEscapes("scheme:///%20space/with? and \n/a\rfile.txt"))
            .isEqualTo("scheme:///%20space/with%3F%20and%20%0A/a%0Dfile.txt");

        then(Vfs2NioUtils.fixUriEscapes("scheme:///")).isEqualTo("scheme:///");
        then(Vfs2NioUtils.fixUriEscapes("scheme://user:password@server:1111/path?query#fragment"))
            .isEqualTo("scheme://user:password@server:1111/path?query#fragment");
    }
}
