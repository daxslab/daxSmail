package com.daxslab.mail.mail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.util.MimeUtil;

import android.test.AndroidTestCase;

import com.daxslab.mail.mail.Message.RecipientType;
import com.daxslab.mail.mail.internet.BinaryTempFileBody;
import com.daxslab.mail.mail.internet.BinaryTempFileMessageBody;
import com.daxslab.mail.mail.internet.MimeBodyPart;
import com.daxslab.mail.mail.internet.MimeHeader;
import com.daxslab.mail.mail.internet.MimeMessage;
import com.daxslab.mail.mail.internet.MimeMultipart;
import com.daxslab.mail.mail.internet.MimeUtility;
import com.daxslab.mail.mail.internet.TextBody;

public class MessageTest extends AndroidTestCase {

    private static final String EIGHT_BIT_RESULT =
              "From: from@example.com\r\n"
            + "To: to@example.com\r\n"
            + "Subject: Test Message\r\n"
            + "Date: Wed, 28 Aug 2013 08:51:09 -0400\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"----Boundary103\"\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "Testing.\r\n"
            + "This is a text body with some greek characters.\r\n"
            + "αβγδεζηθ\r\n"
            + "End of test.\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: base64\r\n"
            + "\r\n"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: message/rfc822\r\n"
            + "Content-Disposition: attachment\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "From: from@example.com\r\n"
            + "To: to@example.com\r\n"
            + "Subject: Test Message\r\n"
            + "Date: Wed, 28 Aug 2013 08:51:09 -0400\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"----Boundary102\"\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "Testing.\r\n"
            + "This is a text body with some greek characters.\r\n"
            + "αβγδεζηθ\r\n"
            + "End of test.\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: base64\r\n"
            + "\r\n"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: message/rfc822\r\n"
            + "Content-Disposition: attachment\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "From: from@example.com\r\n"
            + "To: to@example.com\r\n"
            + "Subject: Test Message\r\n"
            + "Date: Wed, 28 Aug 2013 08:51:09 -0400\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"----Boundary101\"\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "------Boundary101\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + "Testing.\r\n"
            + "This is a text body with some greek characters.\r\n"
            + "αβγδεζηθ\r\n"
            + "End of test.\r\n"
            + "\r\n"
            + "------Boundary101\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary101\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: base64\r\n"
            + "\r\n"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\r\n"
            + "\r\n"
            + "------Boundary101--\r\n"
            + "\r\n"
            + "------Boundary102--\r\n"
            + "\r\n"
            + "------Boundary103--\r\n";

    private static final String SEVEN_BIT_RESULT =
              "From: from@example.com\r\n"
            + "To: to@example.com\r\n"
            + "Subject: Test Message\r\n"
            + "Date: Wed, 28 Aug 2013 08:51:09 -0400\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"----Boundary103\"\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: text/plain;\r\n"
            + " charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: base64\r\n"
            + "\r\n"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\r\n"
            + "\r\n"
            + "------Boundary103\r\n"
            + "Content-Type: message/rfc822\r\n"
            + "Content-Disposition: attachment\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n"
            + "\r\n"
            + "From: from@example.com\r\n"
            + "To: to@example.com\r\n"
            + "Subject: Test Message\r\n"
            + "Date: Wed, 28 Aug 2013 08:51:09 -0400\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"----Boundary102\"\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: base64\r\n"
            + "\r\n"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\r\n"
            + "\r\n"
            + "------Boundary102\r\n"
            + "Content-Type: message/rfc822\r\n"
            + "Content-Disposition: attachment\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n"
            + "\r\n"
            + "From: from@example.com\r\n"
            + "To: to@example.com\r\n"
            + "Subject: Test Message\r\n"
            + "Date: Wed, 28 Aug 2013 08:51:09 -0400\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"----Boundary101\"\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n"
            + "\r\n"
            + "------Boundary101\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary101\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Content-Transfer-Encoding: quoted-printable\r\n"
            + "\r\n"
            + "Testing=2E\r\n"
            + "This is a text body with some greek characters=2E\r\n"
            + "=CE=B1=CE=B2=CE=B3=CE=B4=CE=B5=CE=B6=CE=B7=CE=B8\r\n"
            + "End of test=2E\r\n"
            + "\r\n"
            + "------Boundary101\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: base64\r\n"
            + "\r\n"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\r\n"
            + "\r\n"
            + "------Boundary101--\r\n"
            + "\r\n"
            + "------Boundary102--\r\n"
            + "\r\n"
            + "------Boundary103--\r\n";

    private int mMimeBoundary;

    public MessageTest() {
        super();
    }

    public void testMessage() throws MessagingException, IOException {
        MimeMessage message;
        ByteArrayOutputStream out;

        BinaryTempFileBody.setTempDirectory(getContext().getCacheDir());

        mMimeBoundary = 101;
        message = nestedMessage(nestedMessage(sampleMessage()));
        out = new ByteArrayOutputStream();
        message.writeTo(out);
        assertEquals(EIGHT_BIT_RESULT, out.toString());

        mMimeBoundary = 101;
        message = nestedMessage(nestedMessage(sampleMessage()));
        message.setUsing7bitTransport();
        out = new ByteArrayOutputStream();
        message.writeTo(out);
        assertEquals(SEVEN_BIT_RESULT, out.toString());
    }

    private MimeMessage nestedMessage(MimeMessage subMessage)
            throws MessagingException, IOException {
        BinaryTempFileMessageBody tempMessageBody = new BinaryTempFileMessageBody();
        tempMessageBody.setEncoding(MimeUtil.ENC_8BIT);

        OutputStream out = tempMessageBody.getOutputStream();
        try {
            subMessage.writeTo(out);
        } finally {
            out.close();
        }

        MimeBodyPart bodyPart = new MimeBodyPart(tempMessageBody, "message/rfc822");
        bodyPart.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "attachment");
        bodyPart.setEncoding(MimeUtil.ENC_8BIT);

        MimeMessage parentMessage = sampleMessage();
        ((Multipart) parentMessage.getBody()).addBodyPart(bodyPart);

        return parentMessage;
    }

    private MimeMessage sampleMessage() throws MessagingException, IOException {
        MimeMessage message = new MimeMessage();
        message.setFrom(new Address("from@example.com"));
        message.setRecipient(RecipientType.TO, new Address("to@example.com"));
        message.setSubject("Test Message");
        message.setHeader("Date", "Wed, 28 Aug 2013 08:51:09 -0400");
        message.setEncoding(MimeUtil.ENC_8BIT);

        NonRandomMimeMultipartTest multipartBody = new NonRandomMimeMultipartTest();
        multipartBody.setSubType("mixed");
        multipartBody.addBodyPart(textBodyPart(MimeUtil.ENC_8BIT));
        multipartBody.addBodyPart(textBodyPart(MimeUtil.ENC_QUOTED_PRINTABLE));
        multipartBody.addBodyPart(binaryBodyPart());
        message.setBody(multipartBody);

        return message;
    }

    private MimeBodyPart binaryBodyPart() throws IOException,
            MessagingException {
        String encodedTestString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "abcdefghijklmnopqrstuvwxyz0123456789+/";

        BinaryTempFileBody tempFileBody = new BinaryTempFileBody();

        InputStream in = new Base64InputStream(new ByteArrayInputStream(
                encodedTestString.getBytes("UTF-8")));

        OutputStream out = tempFileBody.getOutputStream();
        try {
            IOUtils.copy(in, out);
        } finally {
            out.close();
        }

        MimeBodyPart bodyPart = new MimeBodyPart(tempFileBody,
                "application/octet-stream");
        bodyPart.setEncoding(MimeUtil.ENC_BASE64);

        return bodyPart;
    }

    private MimeBodyPart textBodyPart(String encoding)
            throws MessagingException {
        TextBody textBody = new TextBody(
                  "Testing.\r\n"
                + "This is a text body with some greek characters.\r\n"
                + "αβγδεζηθ\r\n"
                + "End of test.\r\n");
        textBody.setCharset("utf-8");
        MimeBodyPart bodyPart = new MimeBodyPart(textBody, "text/plain");
        MimeUtility.setCharset("utf-8", bodyPart);
        bodyPart.setEncoding(encoding);
        return bodyPart;
    }

    private class NonRandomMimeMultipartTest extends MimeMultipart {

        public NonRandomMimeMultipartTest() throws MessagingException {
            super();
        }

        @Override
        public String generateBoundary() {
            StringBuilder sb = new StringBuilder();
            sb.append("----Boundary");
            sb.append(Integer.toString(mMimeBoundary++));
            return sb.toString();
        }

    }

}
