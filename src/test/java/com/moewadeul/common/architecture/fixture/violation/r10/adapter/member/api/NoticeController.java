package com.moewadeul.common.architecture.fixture.violation.r10.adapter.member.api;

import com.moewadeul.common.architecture.fixture.violation.r10.adapter.common.mail.MailSender;

public record NoticeController(MailSender mailSender) {}
