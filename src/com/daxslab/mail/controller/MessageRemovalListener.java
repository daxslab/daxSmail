package com.daxslab.mail.controller;

import com.daxslab.mail.mail.Message;

public interface MessageRemovalListener {
    public void messageRemoved(Message message);
}
