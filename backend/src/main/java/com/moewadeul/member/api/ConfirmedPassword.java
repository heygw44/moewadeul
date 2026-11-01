package com.moewadeul.member.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ConfirmedPassword {

    @JsonIgnore
    String passwordToConfirm();

    @JsonIgnore
    String passwordConfirmation();

    @JsonIgnore
    String confirmationFieldName();
}
