/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.utility;

import android.net.Uri;

/**
 * RFC3261 URI Utility methods.
 * Reminder that returned Strings may be URI escaped.
 * All methods assume standard SIP URI unless noted, use the available test methods to confirm proper method to call.
 * Behavior is undefined and likely invalid if the incorrect URI parameter style is supplied.
 * <p>
 * Created by dhon on 11/10/2016.
 */

public class UriSip {
    public static final String SCHEME = "sip:";
    public static final String SCHEME_SECURE = "sips:";
    public static final String DELIMIT = ":";
    public static final char DELIMIT_HOST = '@';
    public static final char DELIMIT_PARAMETERS = ';';
    public static final char DELIMIT_HEADERS = '?';
    public static final char DELIMIT_ENCLOSE_DISPLAY_NAME = '"';
    public static final char ENCLOSE_BEGIN = '<';
    public static final char ENCLOSE_END = '>';
    public static final String CONTACT_FROM = "from:";
    public static final String CONTACT_TO = "to:";


    /**
     * Build URI String from URI escaped parts.
     * All parameters MUST be already URI escaped; otherwise use buildUriStringFromParts
     *
     * @param isSecure   sips: vs sip:
     * @param authority  user:password
     * @param host       example.com
     * @param port       NULL or 1234
     * @param parameters NULL or key=value;key=value;...
     * @param headers    NULL or key=value&key=value&...
     * @return
     */
    public static String buildUriStringFromEscapedParts(boolean isSecure, String authority, String host, String port, String parameters, String headers) {
        StringBuilder sb = new StringBuilder();
        if (isSecure) sb.append(SCHEME_SECURE);
        else sb.append(SCHEME);
        sb.append(authority);
        sb.append(DELIMIT_HOST);
        sb.append(host);
        if (port != null) {
            sb.append(DELIMIT);
            sb.append(port);
        }
        if (parameters != null) {
            sb.append(DELIMIT_PARAMETERS);
            sb.append(parameters);
        }
        if (headers != null) {
            sb.append(DELIMIT_HEADERS);
            sb.append(headers);
        }
        return sb.toString();
    }

    /**
     * Build URI String from non-URI-escaped parameters.
     *
     * @param isSecure   sips: vs sip:
     * @param authority  user:password
     * @param host       example.com
     * @param port       NULL or 1234
     * @param parameters NULL or key=value;key=value;...
     * @param headers    NULL or key=value&key=value&...
     * @return
     */
    public static String buildUriStringFromParts(boolean isSecure, String authority, String host, String port, String parameters, String headers) {
        return buildUriStringFromEscapedParts(isSecure, Uri.encode(authority), Uri.encode(host), Uri.encode(port), Uri.encode(parameters), Uri.encode(headers));
    }

    /**
     * Returns a URI without leading FROM or TO contact headers, or the original String
     *
     * @param uriSipContact Contact style SIP URI
     * @return
     */
    public static String dropContactHeader(String uriSipContact) {
        if (!isContactSipUri(uriSipContact)) return uriSipContact; // not a contact uri, pass-through
        if (uriSipContact.substring(0, CONTACT_FROM.length()).equalsIgnoreCase(CONTACT_FROM))
            return uriSipContact.substring(CONTACT_FROM.length()).trim();
        else if (uriSipContact.substring(0, CONTACT_TO.length()).equalsIgnoreCase(CONTACT_TO))
            return uriSipContact.substring(CONTACT_TO.length()).trim();
        else return uriSipContact; // neither from or to headers found, pass-through
    }

    public static String getAuthority(String uri) {
        return (uri.substring(getSchemaEndPosition(uri), uri.indexOf(DELIMIT_HOST)));
    }

    /**
     * Returns the display name from a Contact style SIP URI
     *
     * @param uriSipContact Contact style SIP URI
     * @return
     */
    public static String getContactDisplayName(String uriSipContact) {
        String uri1 = dropContactHeader(uriSipContact);
        int eBegin = uri1.indexOf(ENCLOSE_BEGIN);
        if (eBegin > 0) {
            int ceBegin = uri1.indexOf(DELIMIT_ENCLOSE_DISPLAY_NAME);
            if (ceBegin != -1) { // display name is enclosed
                int ceEnd = uri1.indexOf(DELIMIT_ENCLOSE_DISPLAY_NAME, ceBegin + 1);
                return uri1.substring(ceBegin + 1, ceEnd).trim();
            } else { // display name is NOT enclosed
                return uri1.substring(0, eBegin).trim();
            }
        } else return null; // no display name
    }

    public static String getHeaders(String uri) {
        int indexHeadersBegin = uri.indexOf(DELIMIT_HEADERS);
        if (indexHeadersBegin < 0) return null;
        return uri.substring(indexHeadersBegin + 1);
    }

    public static String getHost(String uri) {
        int indexHost = uri.indexOf(DELIMIT_HOST) + 1;
        int indexPort = uri.indexOf(DELIMIT, indexHost);
        if (indexPort > 0) return uri.substring(indexHost, indexPort);
        // no port
        int indexParamBegin = uri.indexOf(DELIMIT_PARAMETERS, indexHost);
        if (indexParamBegin > 0) return uri.substring(indexHost, indexParamBegin);
        // no port or params
        int indexHeadersBegin = uri.indexOf(DELIMIT_HEADERS, indexHost);
        if (indexHeadersBegin > 0) return uri.substring(indexHost, indexHeadersBegin);
        // no port or params or headers
        return uri.substring(indexHost);
    }

    public static String getParameters(String uri) {
        int indexParamBegin = uri.indexOf(DELIMIT_PARAMETERS);
        if (indexParamBegin < 0) return null;
        int indexHeadersBegin = uri.indexOf(DELIMIT_HEADERS, indexParamBegin);
        if (indexHeadersBegin > 0) return uri.substring(indexParamBegin + 1, indexHeadersBegin);
        // no params or headers
        return uri.substring(indexParamBegin + 1);
    }

    public static String getPassword(String uri) {
        String authority = getAuthority(uri);
        if (!authority.contains(DELIMIT)) return null; // does not contain a password
        return authority.substring(authority.indexOf(DELIMIT) + 1, authority.length());
    }

    public static String getPort(String uri) {
        int indexHost = uri.indexOf(DELIMIT_HOST);
        int indexPort = uri.indexOf(DELIMIT, indexHost);
        if (indexPort <= 0) return null;
        int indexParamBegin = uri.indexOf(DELIMIT_PARAMETERS, indexHost);
        if (indexParamBegin > 0) return uri.substring(indexPort + 1, indexParamBegin);
        // no params
        int indexHeadersBegin = uri.indexOf(DELIMIT_HEADERS, indexHost);
        if (indexHeadersBegin > 0) return uri.substring(indexPort + 1, indexHeadersBegin);
        // no params or headers
        return uri.substring(indexPort + 1);
    }

    private static int getSchemaEndPosition(String uri) {
        if (isSchemeSecure(uri)) return SCHEME_SECURE.length();
        else return SCHEME.length();
    }

    public static String getUser(String uri) {
        String authority = getAuthority(uri);
        if (!authority.contains(DELIMIT)) return authority; // does not contain a password
        return authority.substring(0, authority.indexOf(DELIMIT));
    }

    /**
     * Is the URI a Contact style SIP URI in the format of:
     * "A. G. Bell" <sip:agb@bell-telephone.com> ;tag=a48s
     *
     * @param uri
     * @return
     */
    public static boolean isContactSipUri(String uri) {
        int eBegin = uri.indexOf(ENCLOSE_BEGIN);
        int eEnd = uri.indexOf(ENCLOSE_END);
        if (eBegin != -1 && eEnd != -1) return true;
        return false;
    }

    public static boolean isSchemeSecure(String uri) {
        return uri.indexOf(SCHEME_SECURE) != -1; // must start with... in case malformed
    }

    /**
     * Basic test for valid Contact style and standard SIP URI syntax.
     *
     * @param uri
     * @return
     */
    public static boolean isValid(String uri) {
        // standard SIP URI
        if (uri.length() < 9) return false; // sip:a@a.a
        if (!(uri.indexOf(SCHEME) == -1 ^ uri.indexOf(SCHEME_SECURE) == -1)) return false;
        if (uri.indexOf(DELIMIT_HOST) < 0) return false;
//         CONTACT style SIP URI
        int eBegin = uri.indexOf(ENCLOSE_BEGIN);
        int eEnd = uri.indexOf(ENCLOSE_END);
        if (eBegin != -1 || eEnd != -1)  // has sip uri enclosure
            if (eBegin != -1 && eEnd != -1) { // has complete enclosure
                if (eBegin > eEnd) return false; // invalid enclosure: ie >xxx<
                if ((eEnd - eBegin) < 9) return false; // enclosed uri too small
                // valid sip uri enclosure, check for display name enclosure
                if (eBegin > 0) { // content before sip uri enclosure
                    int ceBegin = uri.indexOf(DELIMIT_ENCLOSE_DISPLAY_NAME);
                    int ceEnd = uri.indexOf(DELIMIT_ENCLOSE_DISPLAY_NAME, ceBegin + 1);
                    if (ceBegin == -1 ^ ceEnd == -1)
                        return false; // incomplete from enclosure, missing one double quote
                }
            } else return false; // incomplete enclosure
        return true;
    }

    /**
     * Returns a standard SIP URI from a Contact style SIP URI
     *
     * @param uri
     * @return standard SIP URI, or uri if not a Contact-style SIP URI
     */
    public static String stripContactUri(String uri) {
        if (!isContactSipUri(uri)) return uri; // pass through non-contact uri
        int eBegin = uri.indexOf(ENCLOSE_BEGIN) + 1;
        int eEnd = uri.indexOf(ENCLOSE_END);
        return uri.substring(eBegin, eEnd) + uri.substring(eEnd + 1, uri.length());
    }


}
