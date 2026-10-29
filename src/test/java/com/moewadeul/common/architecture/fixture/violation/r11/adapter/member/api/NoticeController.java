package com.moewadeul.common.architecture.fixture.violation.r11.adapter.member.api;

import com.moewadeul.common.architecture.fixture.violation.r11.adapter.common.mail.MailSender;

public record NoticeController(MailSender mailSender) {}
