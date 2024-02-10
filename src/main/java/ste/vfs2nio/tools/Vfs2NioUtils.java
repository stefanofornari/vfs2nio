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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class Vfs2NioUtils {

    public static String encodePath(String path) {
        final String elements[] = path.split("/", -1);

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
        final String elements[] = path.split("/", -1);

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


}
