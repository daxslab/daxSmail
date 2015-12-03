
package com.daxslab.mail.mail.internet;

import com.daxslab.mail.mail.BodyPart;
import com.daxslab.mail.mail.MessagingException;
import com.daxslab.mail.mail.Multipart;

import java.io.*;
import java.util.Locale;
import java.util.Random;

public class MimeMultipart extends Multipart {
    protected String mPreamble;

    protected String mContentType;

    protected String mBoundary;

    protected String mSubType;

    public MimeMultipart() throws MessagingException {
        mBoundary = generateBoundary();
        setSubType("mixed");
    }

    public MimeMultipart(String contentType) throws MessagingException {
        this.mContentType = contentType;
        try {
            mSubType = MimeUtility.getHeaderParameter(contentType, null).split("/")[1];
            mBoundary = MimeUtility.getHeaderParameter(contentType, "boundary");
            if (mBoundary == null) {
                throw new MessagingException("MultiPart does not contain boundary: " + contentType);
            }
        } catch (Exception e) {
            throw new MessagingException(
                "Invalid MultiPart Content-Type; must contain subtype and boundary. ("
                + contentType + ")", e);
        }
    }

    public String generateBoundary() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append("----");
        for (int i = 0; i < 30; i++) {
            sb.append(Integer.toString(random.nextInt(36), 36));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    public String getPreamble() {
        return mPreamble;
    }

    public void setPreamble(String preamble) {
        this.mPreamble = preamble;
    }

    @Override
    public String getContentType() {
        return mContentType;
    }

    public void setSubType(String subType) {
        this.mSubType = subType;
        mContentType = String.format("multipart/%s; boundary=\"%s\"", subType, mBoundary);
    }

    public void writeTo(OutputStream out) throws IOException, MessagingException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);

        if (mPreamble != null) {
            writer.write(mPreamble);
            writer.write("\r\n");
        }

        if (mParts.isEmpty()) {
            writer.write("--");
            writer.write(mBoundary);
            writer.write("\r\n");
        }

        for (int i = 0, count = mParts.size(); i < count; i++) {
            BodyPart bodyPart = mParts.get(i);
            writer.write("--");
            writer.write(mBoundary);
            writer.write("\r\n");
            writer.flush();
            bodyPart.writeTo(out);
            writer.write("\r\n");
        }

        writer.write("--");
        writer.write(mBoundary);
        writer.write("--\r\n");
        writer.flush();
    }

    public InputStream getInputStream() throws MessagingException {
        return null;
    }

    @Override
    public void setUsing7bitTransport() throws MessagingException {
        for (BodyPart part : mParts) {
            part.setUsing7bitTransport();
        }
    }
}
