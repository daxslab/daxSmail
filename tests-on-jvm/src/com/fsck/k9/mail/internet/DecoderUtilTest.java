package com.daxslab.mail.mail.internet;

import junit.framework.TestCase;

public class DecoderUtilTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testDecodeEncodedWords() {
        String body, expect;
        MimeMessage message;

        body = "abc";
        expect = "abc";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?us-ascii?q?abc?=";
        expect = "abc";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?";
        expect = "=?";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=??";
        expect = "=??";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=???";
        expect = "=???";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=????";
        expect = "=????";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=????=";
        expect = "=????=";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=??q??=";
        expect = "=??q??=";;
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=??q?a?=";
        expect = "a";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=??=";
        expect = "=??=";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?x?=";
        expect = "=?x?=";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?x??=";
        expect = "=?x??=";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?x?q?=";
        expect = "=?x?q?=";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?x?q??=";
        expect = "=?x?q??=";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?x?q?X?=";
        expect = "X";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        // invalid base64 string
        body = "=?us-ascii?b?abc?=";
        expect = "";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        // broken encoded header
        body = "=?us-ascii?q?abc?= =?";
        expect = "abc =?";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));

        body = "=?x?= =?";
        expect = "=?x?= =?";
        message = null;
        assertEquals(expect, DecoderUtil.decodeEncodedWords(body, message));
    }
}
