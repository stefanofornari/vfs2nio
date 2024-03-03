/*
 * Vs2Nio
 * ^^^^^^
 *
 * Modifications to the original Copyright (C) 2024 Stefano Fornari.
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
package ste.vfs2nio.tools;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class Vfs2NioUtils {

    public static String encodePath(String path) {
        if (path.equals("/")) {
            return path;
        }

        final String elements[] = splitPathName(path);

        final StringBuilder sb = new StringBuilder();
        for (int i=0; i<elements.length; ++i) {
            if (i>0) {
                sb.append('/');
            }
            sb.append(
                URLEncoder.encode(elements[i], Charset.defaultCharset())
                    //
                    // fix URI encoding; maybe not super efficient but...
                    //
                    .replaceAll("\\+", "%20").replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'").replaceAll("\\%28", "(").replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~")
            );
        }

        return sb.toString();
    }

    public static String decodePath(String path) {
        if (path.equals("/")) {
            return path;
        }

        final String elements[] = splitPathName(path);

        final StringBuilder sb = new StringBuilder();
        for (int i=0; i<elements.length; ++i) {
            if (i>0) {
                sb.append('/');
            }
            sb.append(URLDecoder.decode(elements[i], Charset.defaultCharset()));
        }

        return sb.toString();
    }

    public static String[] decodeElements(final List<String> names) {
        final String[] plainNames = new String[names.size()];
        final AtomicInteger i = new AtomicInteger(0);

        names.forEach(
            (name) -> plainNames[i.getAndIncrement()] = URLDecoder.decode(name, Charset.defaultCharset())
        );

        return plainNames;
    }

    public static String[] splitPathName(final String pathname) {
        if (pathname == null) {
            throw new IllegalArgumentException("pathname can not be null");
        }

        if (pathname.length() == 0) {
            return new String[0];
        }

        if ("/".equals(pathname)) {
            return new String[] {""};
        }

        List<String> elements = new ArrayList();
        int pos = 0, start = 0;

        while ((start < pathname.length() && (pos = pathname.indexOf("/", start)) >= 0)) {
            elements.add(pathname.substring(start, pos));
            start = pos + 1;
        }

        if (start <= pathname.length()) {
            elements.add(pathname.substring(start));
        }

        return elements.toArray(new String[0]);
    }

    public static UriElements extractUriElements(final URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri can not be null");
        }

        String uriString = uri.toString();
        String scheme = null, authority = null, path = null, query = null, fragment = null;

        //
        // NOTE: uriString is a valid URI due to it is not possible to create
        //       and invalid URI
        //

        //
        // search for fragment
        //
        int pos = uriString.indexOf('#');
        if (pos >= 0) {
            fragment = uriString.substring(pos+1);
            uriString = uriString.substring(0, pos);
        }

        //
        // search for query string
        //
        pos = uriString.indexOf('?');
        if (pos > 0) {
            query = uriString.substring(pos+1);
            uriString = uriString.substring(0, pos);
        }

        //
        // search for scheme
        //
        pos = uriString.indexOf("://");
        scheme = uriString.substring(0, pos);
        uriString = uriString.substring(pos+3);

        //
        // search for authority
        //
        pos = uriString.indexOf('/');  // pos can not be -1 because new URI("scheme://") is not a valid URI
        if (pos > 0) {
            authority = uriString.substring(0, pos);
            path = uriString.substring(pos);  // keep the / to make an absolute path
        } else {
            path = uriString;
        }
        path = decodePath(path);

        return new UriElements(scheme, authority, path, query, fragment);
    }

    public static String fixUriEscapes(final String s) {
        //
        // TODO: encode query and fragment ???
        //

        //
        // skip scheme, authority, query and fragment
        //
        int start = s.indexOf("://") + 3; start = Math.max(start, s.indexOf('/', start));
        int end = Math.min(s.indexOf('?'), s.indexOf('#'));

        final String path = decodePath((end < 0) ? s.substring(start) : s.substring(start, end));

        return s.substring(0, start) + encodePath(path) + ((end >= 0) ? s.substring(end) : "");
    }
}
